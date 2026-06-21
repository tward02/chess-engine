package engine.player.evaluator

import com.tward.engine.board.Board
import com.tward.engine.game.ChessGame
import com.tward.engine.player.evaluator.PositionalEvaluator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PositionalEvaluatorTest {

    private val evaluator = PositionalEvaluator()

    private fun eval(fen: String) = evaluator.evaluate(ChessGame(Board.fromFEN(fen)))

    @Test
    fun `symmetric starting position is balanced`() {
        assertEquals(0, evaluator.evaluate(ChessGame(Board.getStartingBoard())))
    }

    @Test
    fun `doubled and isolated pawns are penalised`() {
        // Same three white pawns and no black pawns either way, so material is identical.
        val healthy = eval("4k3/8/8/8/8/8/PPP5/4K3 w - - 0 1")        // connected a2 b2 c2
        val doubledIsolated = eval("4k3/8/8/8/P7/P7/P7/4K3 w - - 0 1") // stacked a2 a3 a4

        assertTrue(
            healthy > doubledIsolated,
            "Connected pawns ($healthy) should beat doubled+isolated pawns ($doubledIsolated)"
        )
    }

    @Test
    fun `a passed pawn is rewarded over a blocked one`() {
        // Each side has one pawn (equal material) on a square with identical piece-square value;
        // only the d6 pawn's passed status differs — the c7 enemy pawn stops it being passed.
        val passed = eval("4k3/6p1/3P4/8/8/8/8/4K3 w - - 0 1")     // black pawn on g7, d6 is passed
        val blocked = eval("4k3/2p5/3P4/8/8/8/8/4K3 w - - 0 1")    // black pawn on c7, d6 not passed

        assertTrue(
            passed > blocked,
            "A passed pawn ($passed) should outscore the same pawn held up by an enemy ($blocked)"
        )
    }

    @Test
    fun `the bishop pair is worth more than a pair of knights`() {
        // c3 and f3 carry the same value in the knight and bishop tables, so the comparison
        // isolates the bishop-pair bonus (plus the minor material edge bishops already have).
        val bishops = eval("4k3/8/8/8/8/2B2B2/8/4K3 w - - 0 1")
        val knights = eval("4k3/8/8/8/8/2N2N2/8/4K3 w - - 0 1")

        assertTrue(
            bishops > knights,
            "Holding the bishop pair ($bishops) should beat the equivalent knights ($knights)"
        )
    }

    @Test
    fun `a rook on an open file is rewarded`() {
        // White keeps a pawn on d2 throughout; only the rook moves, so material is unchanged.
        val openFile = eval("4k3/8/8/8/8/8/3P4/R3K3 w - - 0 1")   // rook a1 — open a-file
        val closedFile = eval("4k3/8/8/8/8/8/3P4/3RK3 w - - 0 1") // rook d1 — own pawn blocks the file

        assertTrue(
            openFile > closedFile,
            "A rook on the open file ($openFile) should outscore one stuck behind a pawn ($closedFile)"
        )
    }
}
