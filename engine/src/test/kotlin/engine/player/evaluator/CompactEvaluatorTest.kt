package engine.player.evaluator

import com.tward.engine.board.Board
import com.tward.engine.game.ChessGame
import com.tward.engine.player.evaluator.CompactEvaluator
import kotlin.test.Test
import kotlin.test.assertTrue

class CompactEvaluatorTest {

    private val evaluator = CompactEvaluator()

    private fun game(fen: String) = ChessGame(Board.fromFEN(fen))

    @Test
    fun `the starting position is close to level`() {
        val score = evaluator.evaluate(game("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"))
        assertTrue(score in 0..25, "Symmetric start should be near zero (tempo only), was $score")
    }

    @Test
    fun `a side a queen up is clearly winning for that side`() {
        // Identical except White has an extra queen on d1.
        val whiteUp = evaluator.evaluate(game("4k3/8/8/8/8/8/8/3QK3 w - - 0 1"))
        assertTrue(whiteUp > 700, "White up a queen should score strongly positive, was $whiteUp")

        val blackUp = evaluator.evaluate(game("3qk3/8/8/8/8/8/8/4K3 w - - 0 1"))
        assertTrue(blackUp < -700, "Black up a queen should score strongly negative, was $blackUp")
    }

    @Test
    fun `more material outweighs less`() {
        // White queen vs black rook: White should be clearly better.
        val score = evaluator.evaluate(game("3rk3/8/8/8/8/8/8/3QK3 w - - 0 1"))
        assertTrue(score > 300, "Queen vs rook should favour White by roughly the difference, was $score")
    }

    @Test
    fun `the score is symmetric under colour reversal`() {
        // Same structure mirrored: White rook on a1 vs Black rook on a8, White to move.
        val score = evaluator.evaluate(game("r3k3/8/8/8/8/8/8/R3K3 w - - 0 1"))
        assertTrue(kotlin.math.abs(score) <= 25, "A mirrored position should be near level, was $score")
    }
}