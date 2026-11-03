package engine.tournament

import com.tward.engine.board.Board
import com.tward.engine.game.ChessGame
import com.tward.engine.tournament.OpeningSuite
import com.tward.engine.tournament.PentanomialSprt
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PentanomialSprtTest {

    @Test
    fun `bounds follow alpha and beta`() {
        val sprt = PentanomialSprt(alpha = 0.05, beta = 0.05)
        assertEquals(-2.944, sprt.lowerBound, 0.001)
        assertEquals(2.944, sprt.upperBound, 0.001)
    }

    @Test
    fun `a clearly stronger candidate passes`() {
        val sprt = PentanomialSprt()
        // Wins 60% of pairs outright, splits the rest: far above +10 Elo.
        var pairs = 0
        while (sprt.status() == PentanomialSprt.Status.CONTINUE && pairs < 10_000) {
            sprt.record(if (pairs % 5 < 3) 1.5 else 1.0)
            pairs++
        }
        assertEquals(PentanomialSprt.Status.PASSED, sprt.status())
        assertTrue(sprt.eloEstimate().low > 0)
    }

    @Test
    fun `an equal candidate fails the plus-ten hypothesis`() {
        val sprt = PentanomialSprt()
        var pairs = 0
        // Symmetric results: mean exactly 0.5 per game.
        val cycle = listOf(1.0, 1.5, 0.5, 1.0, 2.0, 0.0)
        while (sprt.status() == PentanomialSprt.Status.CONTINUE && pairs < 100_000) {
            sprt.record(cycle[pairs % cycle.size])
            pairs++
        }
        assertEquals(PentanomialSprt.Status.FAILED, sprt.status())
        val est = sprt.eloEstimate()
        assertTrue(abs(est.elo) < 1.0 && est.low < 0 && est.high > 0)
    }

    @Test
    fun `identical pairs carry no evidence`() {
        val sprt = PentanomialSprt()
        repeat(50) { sprt.record(1.0) }
        assertEquals(0.0, sprt.llr())
        assertEquals(PentanomialSprt.Status.CONTINUE, sprt.status())
    }

    @Test
    fun `elo conversion round-trips`() {
        assertEquals(100.0, PentanomialSprt.elo(PentanomialSprt.expectedScore(100.0)), 1e-9)
        assertEquals(0.0, PentanomialSprt.elo(0.5), 1e-9)
    }

    @Test
    fun `pair scores outside 0 to 2 are rejected`() {
        assertFailsWith<IllegalArgumentException> { PentanomialSprt().record(2.5) }
    }

    @Test
    fun `bundled opening suite is unique legal positions`() {
        val suite = OpeningSuite.load()
        assertTrue(suite.size >= 200, "suite has ${suite.size} positions")
        assertEquals(suite.size, suite.toSet().size, "duplicate openings")
        for (fen in suite) {
            val game = ChessGame(Board.fromFEN(fen))
            assertTrue(game.getLegalMoves().isNotEmpty(), "no legal moves in $fen")
        }
    }

    @Test
    fun `suite parser skips comments and blanks`() {
        assertEquals(listOf("a", "b"), OpeningSuite.parse("# header\n\na\n  b  \n"))
    }
}
