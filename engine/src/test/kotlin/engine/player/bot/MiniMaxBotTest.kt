package engine.player.bot

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.game.ChessGame
import com.tward.engine.game.GameResult
import com.tward.engine.player.bot.MiniMaxBot
import com.tward.engine.player.evaluator.StandardEvaluator
import kotlin.test.Test
import kotlin.test.assertEquals

class MiniMaxBotTest {

    private fun game(fen: String): ChessGame {
        return ChessGame(Board.fromFEN(fen))
    }

    @Test
    fun `white finds mate in one`() {

        // Back rank mate: Ra8#
        val game =
            game("6k1/5ppp/8/8/8/8/8/R3K3 w - - 0 1")

        val bot = MiniMaxBot(depth = 2, colour = Colour.WHITE)
        val move = bot.chooseMove(game)

        game.makeMove(move)

        assertEquals(GameResult.WHITE_WIN, game.getGameResult())
    }

    @Test
    fun `black finds mate in one`() {

        // Back rank mate: Ra1#
        val game =
            game("r3k3/8/8/8/8/8/5PPP/6K1 b - - 0 1")

        val bot = MiniMaxBot(depth = 2, colour = Colour.BLACK)
        val move = bot.chooseMove(game)

        game.makeMove(move)

        assertEquals(GameResult.BLACK_WIN, game.getGameResult())
    }

    @Test
    fun `white captures hanging queen`() {

        val game =
            game("4k3/8/8/3q4/4P3/8/8/4K3 w - - 0 1")

        val bot = MiniMaxBot(depth = 3, colour = Colour.WHITE)
        val move = bot.chooseMove(game)

        assertEquals("e4", move.from.toString())
        assertEquals("d5", move.to.toString())
    }

    @Test
    fun `black captures hanging queen`() {

        val game =
            game("4k3/8/8/4p3/3Q4/8/8/4K3 b - - 0 1")

        val bot = MiniMaxBot(depth = 3, colour = Colour.BLACK)
        val move = bot.chooseMove(game)

        assertEquals("e5", move.from.toString())
        assertEquals("d4", move.to.toString())
    }

    @Test
    fun `white finds mate in one with standard evaluator`() {

        val game =
            game("6k1/5ppp/8/8/8/8/8/R3K3 w - - 0 1")

        val bot = MiniMaxBot(depth = 2, colour = Colour.WHITE, evaluator = StandardEvaluator())
        val move = bot.chooseMove(game)

        game.makeMove(move)

        assertEquals(GameResult.WHITE_WIN, game.getGameResult())
    }
}
