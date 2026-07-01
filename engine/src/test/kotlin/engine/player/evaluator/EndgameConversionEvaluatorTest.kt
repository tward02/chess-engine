package engine.player.evaluator

import com.tward.engine.board.Board
import com.tward.engine.game.ChessGame
import com.tward.engine.player.evaluator.CompactEvaluator
import com.tward.engine.player.evaluator.EndgameConversionEvaluator
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EndgameConversionEvaluatorTest {

    private val evaluator = EndgameConversionEvaluator()

    private fun eval(fen: String) = evaluator.evaluate(ChessGame(Board.fromFEN(fen)))

    @Test
    fun `cornering the losing king scores better than letting it centralise`() {
        // Same K+Q vs K material; only the king placements differ.
        val cornered = eval("k7/8/1K6/8/8/8/8/3Q4 w - - 0 1")    // black king boxed in the corner
        val centred = eval("8/8/8/3k4/8/8/8/Q6K w - - 0 1")      // black king free in the centre
        assertTrue(cornered > centred, "mop-up must reward driving the king to the edge ($cornered vs $centred)")
    }

    @Test
    fun `bringing the winning king closer scores better`() {
        val close = eval("k7/2K5/8/8/3Q4/8/8/8 w - - 0 1")       // white king two steps from the black king
        val far = eval("k7/8/8/8/3Q4/8/8/7K w - - 0 1")          // white king in the opposite corner
        assertTrue(close > far, "mop-up must reward king proximity ($close vs $far)")
    }

    @Test
    fun `the score fades as the halfmove clock approaches the 50-move draw`() {
        val fresh = eval("4k3/8/8/8/8/8/8/QK6 w - - 0 1")
        val stale = eval("4k3/8/8/8/8/8/8/QK6 w - - 90 60")
        assertTrue(abs(stale) < abs(fresh), "a nearly-expired clock must shrink the score ($fresh vs $stale)")
        assertTrue(fresh > 0 == stale > 0 || stale == 0, "fading must never flip the sign")
    }

    @Test
    fun `no mop-up or fade outside won late endgames`() {
        // Starting position: full material, clock at zero — must match CompactEvaluator exactly.
        val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        assertEquals(CompactEvaluator().evaluate(ChessGame(Board.fromFEN(fen))), eval(fen))
    }

    @Test
    fun `mop-up works for black as the winning side too`() {
        val cornered = eval("8/8/8/8/8/6k1/5q2/7K b - - 0 1")    // white king cornered by black
        val centred = eval("8/8/8/4K3/8/6k1/5q2/8 b - - 0 1")    // white king centralised
        assertTrue(cornered < centred, "black winning: cornering white must push the score further negative")
    }
}
