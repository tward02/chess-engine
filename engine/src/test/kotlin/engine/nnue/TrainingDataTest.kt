package engine.nnue

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.nnue.PackedPosition
import com.tward.engine.nnue.TrainingData
import com.tward.engine.nnue.TrainingSet
import com.tward.engine.player.evaluator.nnue.NnueFeatures
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrainingDataTest {

    private val lines = listOf(
        "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1;-12;0.5",
        "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQ1RK1 w kq - 6 5;35;1",
        "8/8/4k3/8/8/3K4/6P1/8 b - - 0 60;250.0;0",
        "4k3/8/8/8/8/8/8/Q3K3 w - - 0 1;900;1"
    )

    /** What the old Board-based loader produced for a line: White-perspective features, sorted. */
    private fun boardFeatures(fen: String): List<Int> =
        Board.fromFEN(fen).getPiecesWithSquares()
            .map { (square, piece) -> NnueFeatures.index(Colour.WHITE, piece, square) }
            .sorted()

    private fun setFeatures(set: TrainingSet, i: Int): List<Int> {
        val out = IntArray(32)
        val n = set.features(i, out)
        return out.take(n).sorted()
    }

    @Test
    fun `text parsing matches the board's own features, side to move and labels`(@TempDir dir: Path) {
        val file = dir.resolve("data.txt")
        Files.write(file, lines)
        val set = TrainingSet()
        TrainingData.load(file, set, seen = null)

        assertEquals(lines.size, set.size)
        lines.forEachIndexed { i, line ->
            val (fen, score, result) = line.split(';')
            assertEquals(boardFeatures(fen), setFeatures(set, i), line)
            val whiteToMove = fen.split(' ')[1] == "w"
            assertEquals(whiteToMove, set.whiteToMove(i))
            val whiteScore = score.toFloat().toInt()
            assertEquals(if (whiteToMove) whiteScore else -whiteScore, set.score(i))
            assertEquals(if (whiteToMove) result.toFloat() else 1f - result.toFloat(), set.result(i))
        }
    }

    @Test
    fun `binary packing round-trips every position`(@TempDir dir: Path) {
        val text = dir.resolve("data.txt")
        Files.write(text, lines)
        val binary = TrainingData.binaryFor(text)
        assertEquals("data.bin", binary.fileName.toString())

        assertEquals(lines.size, TrainingData.pack(text, binary))
        assertTrue(TrainingData.isBinary(binary))
        assertFalse(TrainingData.isBinary(text))
        assertTrue(Files.size(binary) < Files.size(text) / 2, "binary should be well under half the text size")

        val fromText = TrainingSet().also { TrainingData.load(text, it, null) }
        val fromBinary = TrainingSet().also { TrainingData.load(binary, it, null) }
        assertEquals(fromText.size, fromBinary.size)
        val a = PackedPosition()
        val b = PackedPosition()
        for (i in 0 until fromText.size) {
            fromText.get(i, a)
            fromBinary.get(i, b)
            assertEquals(a.occupancy, b.occupancy)
            assertEquals(a.kindsLow, b.kindsLow)
            assertEquals(a.kindsHigh, b.kindsHigh)
            assertEquals(a.whiteToMove, b.whiteToMove)
            assertEquals(a.whiteScore, b.whiteScore)
            assertEquals(a.whiteResult2, b.whiteResult2)
            assertEquals(a.key(), b.key())
        }
    }

    @Test
    fun `malformed lines are skipped and counted, never thrown`(@TempDir dir: Path) {
        val file = dir.resolve("bad.txt")
        Files.write(
            file, listOf(
                lines[0],
                "not a fen;1;1",
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP w KQkq - 0 1;0;0.5",       // 7 ranks
                "rnbqkbnr/pppppppp/9/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1;0;1", // 9 files
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR x KQkq - 0 1;0;1", // bad side
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1;abc;1",
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1;0;0.7",
                "",
                lines[1]
            )
        )
        val set = TrainingSet()
        val stats = TrainingData.load(file, set, null)
        assertEquals(2, stats.loaded)
        assertEquals(6, stats.skipped)
    }

    @Test
    fun `the dedupe key ignores castling, en passant and clocks but not the side to move`() {
        fun key(line: String) = PackedPosition().also { assertTrue(TrainingData.parseText(line, it)) }.key()
        val base = key("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1;0;0.5")
        assertEquals(base, key("r3k2r/8/8/8/8/8/8/R3K2R w - - 12 30;5;1"))
        assertTrue(base != key("r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1;0;0.5"))
        assertTrue(base != key("r3k2r/8/8/8/8/8/8/R3K1R1 w Qkq - 0 1;0;0.5"))
    }
}
