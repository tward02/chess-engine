package engine.nnue

import com.tward.engine.board.Board
import com.tward.engine.game.ChessGame
import com.tward.engine.nnue.NnueTrainer
import com.tward.engine.player.evaluator.NnueEvaluator
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
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
        val net = NnueTrainer(hiddenSize = 8, epochs = 60, seed = 1L).train(data, out)

        val evaluator = NnueEvaluator(net)
        fun eval(fen: String) = evaluator.evaluate(ChessGame(Board.fromFEN(fen)))

        assertTrue(eval("4k3/8/8/8/8/8/8/Q3K3 w - - 0 1") > 100, "queen up must score clearly positive")
        assertTrue(eval("q3k3/8/8/8/8/8/8/4K3 w - - 0 1") < -100, "queen down must score clearly negative")
        assertTrue(Files.exists(out), "the trained network must be checkpointed to disk")
    }
}
