package engine.player.bot

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.game.ChessGame
import com.tward.engine.game.GameResult
import com.tward.engine.player.bot.NeuralNegamaxBot
import com.tward.engine.player.evaluator.NnueEvaluator
import engine.player.evaluator.nnue.TestNetworks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NeuralNegamaxBotTest {

    private fun game(fen: String) = ChessGame(Board.fromFEN(fen))

    // The deterministic material network keeps these tests independent of whichever trained
    // network happens to be bundled.
    private fun bot(colour: Colour, depth: Int) = NeuralNegamaxBot(
        colour = colour, fixedDepth = depth, useOpeningBookMoves = false,
        evaluator = NnueEvaluator(TestNetworks.material())
    )

    @Test
    fun `white finds mate in one`() {
        val game = game("6k1/5ppp/8/8/8/8/8/R3K3 w - - 0 1")
        val move = bot(Colour.WHITE, depth = 2).chooseMove(game)
        game.makeMove(move)
        assertEquals(GameResult.WHITE_WIN, game.getGameResult())
    }

    @Test
    fun `black finds mate in one`() {
        val game = game("r3k3/8/8/8/8/8/5PPP/6K1 b - - 0 1")
        val move = bot(Colour.BLACK, depth = 2).chooseMove(game)
        game.makeMove(move)
        assertEquals(GameResult.BLACK_WIN, game.getGameResult())
    }

    @Test
    fun `white captures a hanging queen`() {
        val game = game("4k3/8/8/3q4/4P3/8/8/4K3 w - - 0 1")
        val move = bot(Colour.WHITE, depth = 4).chooseMove(game)
        assertEquals("e4", move.from.toString())
        assertEquals("d5", move.to.toString())
    }

    @Test
    fun `the search restores the game state it explored`() {
        val game = game("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 0 1")
        val before = game.board.toFEN()
        bot(Colour.WHITE, depth = 5).chooseMove(game)
        assertEquals(before, game.board.toFEN(), "make/undo must leave the board untouched")
    }

    @Test
    fun `the bundled network plays a legal move under a time budget`() {
        val game = game("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 0 1")
        val bot = NeuralNegamaxBot(colour = Colour.WHITE, useOpeningBookMoves = false, maxThinkTimeMillis = 500)

        val move = bot.chooseMove(game, timeLeft = 3000)

        assertTrue(game.getLegalMoves().contains(move), "must return a legal move")
        assertTrue(bot.nodesSearched > 0)
    }
}
