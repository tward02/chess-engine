package engine.player.evaluator

import com.tward.engine.board.Board
import com.tward.engine.game.ChessGame
import com.tward.engine.player.evaluator.BasicEvaluator
import com.tward.engine.player.evaluator.QuiescenceEvaluator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuiescenceEvaluatorTest {

    // Material-only base keeps the expected numbers deterministic and easy to reason about
    private val base = BasicEvaluator()
    private val quiescence = QuiescenceEvaluator(base)

    private fun game(fen: String) = ChessGame(Board.fromFEN(fen))

    @Test
    fun `quiet positions are scored exactly like the base evaluator`() {
        val quiet = game("4k3/8/8/8/8/8/8/4K3 w - - 0 1")
        assertEquals(base.evaluate(quiet), quiescence.evaluate(quiet))
    }

    @Test
    fun `a hanging queen mid-exchange is resolved instead of read as a swing`() {
        // White queen on e5 is attacked by the black d6 pawn; Black is to move.
        // Statically White looks up Q for P (+800), but Black just plays dxe5.
        val position = game("4k3/8/3p4/4Q3/8/8/8/4K3 b - - 0 1")

        val staticScore = base.evaluate(position)
        val resolvedScore = quiescence.evaluate(position)

        assertTrue(staticScore > 700, "Static score should show White up a queen ($staticScore)")
        assertTrue(
            resolvedScore < staticScore,
            "Quiescence ($resolvedScore) should see the queen recaptured, not the raw swing ($staticScore)"
        )
        // After dxe5 Black is up a pawn; the search should land near there, not at +800
        assertTrue(
            resolvedScore < 0,
            "After the recapture Black is ahead, so the resolved score should be negative ($resolvedScore)"
        )
    }

    @Test
    fun `an equal queen trade settles back to level`() {
        // Queens face off on the d-file with both kings able to recapture; otherwise symmetric.
        val position = game("3qk3/8/8/8/8/8/8/3QK3 w - - 0 1")
        val resolved = quiescence.evaluate(position)

        assertEquals(
            0, resolved,
            "An available but even queen trade should leave the score level (was $resolved)"
        )
    }

    @Test
    fun `the search leaves the game state untouched`() {
        val position = game("4k3/8/3p4/4Q3/8/8/8/4K3 b - - 0 1")
        val before = position.board.toFEN()

        quiescence.evaluate(position)

        assertEquals(before, position.board.toFEN(), "Quiescence must restore the board it searched")
    }
}
