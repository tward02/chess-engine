package engine.nnue

import com.tward.engine.board.Board
import com.tward.engine.game.ChessGame
import com.tward.engine.nnue.LearningRateSchedule
import com.tward.engine.nnue.NnueTrainer
import com.tward.engine.player.evaluator.NnueEvaluator
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NnueTrainerTest {

    @Test
    fun `training on material imbalances teaches the network which side is winning`(@TempDir dir: Path) {
        // Tiny overfit set: queen-up and rook-up positions for each colour, labelled with the
        // material score and the eventual (assumed) result.
        val lines = listOf(
            "4k3/8/8/8/8/8/8/Q3K3 w - - 0 1;900;1",
            "4k3/8/8/8/8/8/8/Q3K3 b - - 0 1;900;1",
            "q3k3/8/8/8/8/8/8/4K3 w - - 0 1;-900;0",
            "q3k3/8/8/8/8/8/8/4K3 b - - 0 1;-900;0",
            "4k3/8/8/8/8/8/8/R3K3 w - - 0 1;500;1",
            "r3k3/8/8/8/8/8/8/4K3 b - - 0 1;-500;0"
        )
        val data = dir.resolve("data.txt")
        Files.write(data, List(20) { lines }.flatten())   // repeat so an epoch has some batches

        val out = dir.resolve("net.nnue")
        // The repetition is deliberate (more steps per epoch), so deduplication is off here.
        val net = NnueTrainer(
            hiddenSize = 8, epochs = 60, seed = 1L, dedupe = false, batchSize = 8, threads = 2, patience = 0
        ).train(data, out)

        val evaluator = NnueEvaluator(net)
        fun eval(fen: String) = evaluator.evaluate(ChessGame(Board.fromFEN(fen)))

        assertTrue(eval("4k3/8/8/8/8/8/8/Q3K3 w - - 0 1") > 100, "queen up must score clearly positive")
        assertTrue(eval("q3k3/8/8/8/8/8/8/4K3 w - - 0 1") < -100, "queen down must score clearly negative")
        assertTrue(Files.exists(out), "the trained network must be checkpointed to disk")
    }

    @Test
    fun `reads UTF-16 data files, as written by PowerShell redirection`(@TempDir dir: Path) {
        // PowerShell's `>` and Set-Content write UTF-16 LE with a BOM by default; combining
        // generation runs that way must not break training.
        val lines = List(10) { "4k3/8/8/8/8/8/8/Q3K3 w - - 0 1;900;1" }
        val data = dir.resolve("utf16.txt")
        Files.write(data, lines.joinToString("\r\n").toByteArray(Charsets.UTF_16LE).let {
            byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + it   // BOM
        })

        val net = NnueTrainer(hiddenSize = 4, epochs = 1, seed = 1L).train(data, dir.resolve("net.nnue"))

        assertTrue(net.hiddenSize == 4, "training must succeed on a UTF-16 data file")
    }

    @Test
    fun `duplicate positions are dropped unless dedupe is off`(@TempDir dir: Path) {
        val data = dir.resolve("dupes.txt")
        Files.write(
            data, List(5) { "4k3/8/8/8/8/8/8/Q3K3 w - - 0 1;900;1" } + "4k3/8/8/8/8/8/8/Q3K3 b - - 0 1;900;1"
        )
        val output = java.io.ByteArrayOutputStream()
        val original = System.out
        System.setOut(java.io.PrintStream(output, true))
        try {
            NnueTrainer(hiddenSize = 4, epochs = 1, seed = 1L).train(data, dir.resolve("a.nnue"))
            NnueTrainer(hiddenSize = 4, epochs = 1, seed = 1L, dedupe = false).train(data, dir.resolve("b.nnue"))
        } finally {
            System.setOut(original)
        }
        val log = output.toString()
        assertTrue("Dropped 4 duplicate positions" in log, log)
        assertTrue("train=2 " in log && "train=6 " in log, log)
    }

    private fun materialData(dir: Path): Path {
        val data = dir.resolve("material.txt")
        Files.write(
            data, listOf(
                "4k3/8/8/8/8/8/8/Q3K3 w - - 0 1;900;1",
                "q3k3/8/8/8/8/8/8/4K3 w - - 0 1;-900;0",
                "4k3/8/8/8/8/8/8/R3K3 b - - 0 1;500;1",
                "r3k3/8/8/8/8/8/8/4K3 b - - 0 1;-500;0",
                "4k3/8/8/8/8/8/8/N3K3 w - - 0 1;300;0.5",
                "n3k3/8/8/8/8/8/8/4K3 b - - 0 1;-300;0.5"
            )
        )
        return data
    }

    @Test
    fun `training is deterministic for a seed and thread count`(@TempDir dir: Path) {
        val data = materialData(dir)
        fun run(name: String) = NnueTrainer(hiddenSize = 8, epochs = 5, seed = 7L, batchSize = 2, threads = 3, validationFraction = 0f)
            .train(data, dir.resolve(name))
        val a = run("a.nnue")
        val b = run("b.nnue")
        assertTrue(a.ftWeights.contentEquals(b.ftWeights) && a.outWeights.contentEquals(b.outWeights) && a.outBias == b.outBias)
    }

    @Test
    fun `the cosine schedule decays from the initial to the minimum rate`() {
        val trainer = NnueTrainer(learningRate = 0.01f, minLearningRate = 0.001f, schedule = LearningRateSchedule.COSINE)
        assertEquals(0.01f, trainer.learningRateAt(0, 101), 1e-7f)
        assertEquals(0.0055f, trainer.learningRateAt(50, 101), 1e-6f)
        assertEquals(0.001f, trainer.learningRateAt(100, 101), 1e-7f)
        val constant = NnueTrainer(learningRate = 0.01f, schedule = LearningRateSchedule.CONSTANT)
        assertEquals(0.01f, constant.learningRateAt(100, 101))
    }

    @Test
    fun `training stops early once validation stops improving`(@TempDir dir: Path) {
        // Validation is block 0 of every 2 (val=0.5 with tiny blocks would need 512+ lines), so build
        // 1024 lines: the first block of 512 trains, the second validates on unrelated labels.
        val train = List(512) { "4k3/8/8/8/8/8/8/Q3K3 w - - 0 1;900;1" }
        val validate = List(512) { "4k3/8/8/8/8/8/8/Q3K3 w - - 0 1;-900;0" }
        val data = dir.resolve("conflict.txt")
        Files.write(data, train + validate)
        val log = captureOutput {
            NnueTrainer(hiddenSize = 4, epochs = 30, seed = 1L, dedupe = false, validationFraction = 0.5f,
                batchSize = 64, threads = 1, patience = 2).train(data, dir.resolve("net.nnue"))
        }
        assertTrue("Stopping early" in log, log)
        assertFalse("epoch 30/30" in log, log)
    }

    @Test
    fun `weight decay shrinks the weights and zero decay changes nothing`(@TempDir dir: Path) {
        val lines = listOf(
            "4k3/8/8/8/8/8/8/Q3K3 w - - 0 1;900;1",
            "q3k3/8/8/8/8/8/8/4K3 w - - 0 1;-900;0",
            "4k3/8/8/8/8/8/8/R3K3 b - - 0 1;500;1",
            "r3k3/8/8/8/8/8/8/4K3 b - - 0 1;-500;0"
        )
        val data = dir.resolve("data.txt")
        Files.write(data, List(20) { lines }.flatten())

        fun train(name: String, decay: Float?) = NnueTrainer(
            hiddenSize = 8, epochs = 20, seed = 1L, dedupe = false, batchSize = 8, threads = 2, patience = 0,
            validationFraction = 0f, weightDecay = decay ?: 0f
        ).train(data, dir.resolve(name))

        val plain = train("plain.nnue", null)
        val zero = train("zero.nnue", 0f)
        val decayed = train("decayed.nnue", 10f)

        assertTrue(
            Files.readAllBytes(dir.resolve("plain.nnue")).contentEquals(Files.readAllBytes(dir.resolve("zero.nnue"))),
            "decay=0 must train exactly like no decay"
        )
        fun sumSquares(w: FloatArray) = w.sumOf { (it * it).toDouble() }
        assertTrue(sumSquares(decayed.ftWeights) < sumSquares(plain.ftWeights), "first-layer weights must shrink")
        assertTrue(sumSquares(decayed.outWeights) < sumSquares(plain.outWeights), "output weights must shrink")
        assertEquals(zero.hiddenSize, decayed.hiddenSize)

        val evaluator = NnueEvaluator(decayed)
        assertTrue(
            evaluator.evaluate(ChessGame(Board.fromFEN("4k3/8/8/8/8/8/8/Q3K3 w - - 0 1"))) > 0,
            "a decayed net must still learn which side is winning"
        )
    }

    private fun captureOutput(block: () -> Unit): String {
        val output = java.io.ByteArrayOutputStream()
        val original = System.out
        System.setOut(java.io.PrintStream(output, true))
        try {
            block()
        } finally {
            System.setOut(original)
        }
        return output.toString()
    }
}
