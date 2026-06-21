package engine.player.bot

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.game.ChessGame
import com.tward.engine.game.GameResult
import com.tward.engine.player.bot.NegamaxBot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class NegamaxBotTest {

    private fun game(fen: String) = ChessGame(Board.fromFEN(fen))

    // Fixed depth + no book = deterministic search that never touches the clock.
    private fun bot(colour: Colour, depth: Int) =
        NegamaxBot(colour = colour, fixedDepth = depth, useOpeningBookMoves = false)

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

        val move = bot(Colour.WHITE, depth = 3).chooseMove(game)

        assertEquals("e4", move.from.toString())
        assertEquals("d5", move.to.toString())
    }

    @Test
    fun `black captures a hanging queen`() {
        val game = game("4k3/8/8/4p3/3Q4/8/8/4K3 b - - 0 1")

        val move = bot(Colour.BLACK, depth = 3).chooseMove(game)

        assertEquals("e5", move.from.toString())
        assertEquals("d4", move.to.toString())
    }

    @Test
    fun `quiescence stops the bot capturing into a recapture and losing its queen`() {
        // White Qh5 is attacked by the g6 pawn. The greedy Qxg6 wins a pawn at the search horizon
        // but f7xg6 recaptures the queen one ply later. At depth 1 only quiescence can see that, so
        // a horizon-limited search would blunder here and a quiescent one must not.
        val game = game("4k3/5p2/6p1/7Q/8/8/8/4K3 w - - 0 1")

        val move = bot(Colour.WHITE, depth = 1).chooseMove(game)

        assertNotEquals("g6", move.to.toString(), "Qxg6?? hangs the queen to f7xg6")
        // The queen should still be on the board after the move it chose.
        game.makeMove(move)
        val whiteQueens = game.board.getPieces().count {
            it.colour == Colour.WHITE && it.type == com.tward.engine.board.PieceType.QUEEN
        }
        assertEquals(1, whiteQueens, "White should keep its queen")
    }

    @Test
    fun `white forces a mate in two`() {
        // 1.Ra8+ Kh7 (forced) 2.Qg6# — the Qg6 mate is supported by the h5 pawn.
        val game = game("6k1/5pp1/7p/7P/4Q3/8/8/R5K1 w - - 0 1")

        val firstMove = bot(Colour.WHITE, depth = 4).chooseMove(game)
        game.makeMove(firstMove)

        if (game.getGameResult() == GameResult.WHITE_WIN) return  // an even faster mate is fine

        // Every black reply must be checkmated by White's next move.
        for (reply in game.getLegalMoves()) {
            val line = game.copy()
            line.makeMove(reply)
            val mateMove = bot(Colour.WHITE, depth = 4).chooseMove(line)
            line.makeMove(mateMove)
            assertEquals(
                GameResult.WHITE_WIN, line.getGameResult(),
                "After ${firstMove.toAlgebraic()} ${reply.toAlgebraic()} White should mate, played ${mateMove.toAlgebraic()}"
            )
        }
    }

    @Test
    fun `the search restores the game state it explored`() {
        val game = game("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 0 1")
        val before = game.board.toFEN()

        bot(Colour.WHITE, depth = 4).chooseMove(game)

        assertEquals(before, game.board.toFEN(), "make/undo must leave the board untouched")
    }

    @Test
    fun `deeper iterative search finds a strong capture under a time budget`() {
        // Time-bounded path (no fixedDepth): the free queen on d5 must still be taken.
        val game = game("4k3/8/8/3q4/4P3/8/8/4K3 w - - 0 1")
        val bot = NegamaxBot(colour = Colour.WHITE, useOpeningBookMoves = false)

        val move = bot.chooseMove(game, timeLeft = 2000)

        assertEquals("e4", move.from.toString())
        assertEquals("d5", move.to.toString())
        assertTrue(bot.nodesSearched > 0, "the bot should have searched some nodes")
    }
}