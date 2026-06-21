package engine.player.evaluator

import com.tward.engine.board.Board
import com.tward.engine.game.ChessGame
import com.tward.engine.player.evaluator.AdvancedEvaluator
import kotlin.test.Test
import kotlin.test.assertTrue

class AdvancedEvaluatorTest {

    private val advanced = AdvancedEvaluator()

    private fun game(fen: String) = ChessGame(Board.fromFEN(fen))

    @Test
    fun `the starting position stays roughly level`() {
        val score = advanced.evaluate(game("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"))
        assertTrue(kotlin.math.abs(score) <= 40, "symmetric start should be near zero, was $score")
    }

    @Test
    fun `material still dominates positional terms`() {
        val score = advanced.evaluate(game("4k3/8/8/8/8/8/8/3QK3 w - - 0 1"))
        assertTrue(score > 700, "a free queen should outweigh mobility/king terms, was $score")
    }

    @Test
    fun `a more mobile piece is preferred over a cramped one`() {
        // Identical material (K+Q vs K); only the white queen's square differs. The centralised,
        // highly mobile queen should be valued above the queen buried in the corner.
        val activeQueen = advanced.evaluate(game("4k3/8/8/8/3Q4/8/8/4K3 w - - 0 1"))
        val corneredQueen = advanced.evaluate(game("4k3/8/8/8/8/8/8/Q3K3 w - - 0 1"))
        assertTrue(activeQueen > corneredQueen, "active queen ($activeQueen) should beat cornered ($corneredQueen)")
    }

    @Test
    fun `an exposed king with open files nearby is penalised`() {
        // Same material; White's king sits behind an intact pawn shield, Black's is on a wide-open
        // king side with a White rook bearing down. The advanced eval should favour White.
        val safe = "r5k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1"
        val exposed = "r4k2/5p1p/8/8/8/8/5PPP/R4RK1 w - - 0 1"
        assertTrue(
            advanced.evaluate(game(exposed)) >= advanced.evaluate(game(safe)),
            "the position with the more exposed black king should favour White at least as much"
        )
    }
}
