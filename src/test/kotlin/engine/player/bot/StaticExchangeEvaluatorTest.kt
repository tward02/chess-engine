package engine.player.bot

import com.tward.engine.board.Board
import com.tward.engine.game.MoveGenerator
import com.tward.engine.player.bot.StaticExchangeEvaluator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StaticExchangeEvaluatorTest {

    private fun see(fen: String, from: String, to: String): Int {
        val board = Board.fromFEN(fen)
        val move = MoveGenerator(board).generateLegalMoves().first {
            it.from.toString() == from && it.to.toString() == to
        }
        return StaticExchangeEvaluator.evaluate(board, move)
    }

    @Test
    fun `capturing an undefended piece wins its full value`() {
        // White pawn e4 takes an undefended knight on d5.
        assertEquals(320, see("4k3/8/8/3n4/4P3/8/8/4K3 w - - 0 1", "e4", "d5"))
    }

    @Test
    fun `a rook taking a pawn defended by a pawn loses the exchange`() {
        // Rxe5 then dxe5: win a pawn (100), lose a rook (500) -> -400.
        assertEquals(-400, see("4k3/8/3p4/4p3/8/8/8/4R1K1 w - - 0 1", "e1", "e5"))
    }

    @Test
    fun `an even pawn trade evaluates to zero`() {
        // dxe5, fxe5: pawn for pawn.
        assertEquals(0, see("4k3/8/5p2/4p3/3P4/8/8/4K3 w - - 0 1", "d4", "e5"))
    }

    @Test
    fun `winning a queen for a pawn is strongly positive`() {
        // exd5 grabs the queen; only the king can recapture the pawn.
        val score = see("8/8/4k3/3q4/4P3/8/8/4K3 w - - 0 1", "e4", "d5")
        assertTrue(score > 700, "expected to win roughly a queen for a pawn, was $score")
    }
}
