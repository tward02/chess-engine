package engine.player.evaluator

import com.tward.engine.board.Board
import com.tward.engine.game.ChessGame
import com.tward.engine.player.evaluator.AdaptiveEvaluator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdaptiveEvaluatorTest {

    private val evaluator = AdaptiveEvaluator()

    private fun eval(fen: String) = evaluator.evaluate(ChessGame(Board.fromFEN(fen)))

    @Test
    fun `symmetric starting position is balanced`() {
        assertEquals(0, evaluator.evaluate(ChessGame(Board.getStartingBoard())))
    }

    @Test
    fun `in the endgame a central king is better than a back-rank king`() {
        // Same material (bare kings); only White's king differs
        val centralKing = eval("4k3/8/8/8/4K3/8/8/8 w - - 0 1")
        val backRankKing = eval("4k3/8/8/8/8/8/8/4K3 w - - 0 1")

        assertTrue(
            centralKing > backRankKing,
            "Central king ($centralKing) should outscore back-rank king ($backRankKing) in the endgame"
        )
    }

    @Test
    fun `in the endgame driving the enemy king to the corner is rewarded`() {
        // White king active in the centre; compare black king centralised vs cornered
        val enemyCentral = eval("8/8/8/3k4/8/3K4/8/8 w - - 0 1")
        val enemyCornered = eval("k7/8/8/8/8/3K4/8/8 w - - 0 1")

        assertTrue(
            enemyCornered > enemyCentral,
            "Cornering the black king ($enemyCornered) should beat letting it centralise ($enemyCentral)"
        )
    }

    @Test
    fun `in the endgame an advanced passed pawn is favoured`() {
        // White pawn near promotion vs the same pawn on its starting square
        val advanced = eval("4k3/8/8/8/3P4/8/8/4K3 w - - 0 1")
        val home = eval("4k3/8/8/8/8/8/3P4/4K3 w - - 0 1")

        assertTrue(
            advanced > home,
            "Advanced pawn ($advanced) should outscore the same pawn at home ($home)"
        )
    }
}
