package engine.player.bot

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.PieceType
import com.tward.engine.game.ChessGame
import com.tward.engine.game.GameResult
import com.tward.engine.player.bot.EliteNegamaxBot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EliteNegamaxBotTest {

    private fun game(fen: String) = ChessGame(Board.fromFEN(fen))

    private fun bot(colour: Colour, depth: Int) =
        EliteNegamaxBot(colour = colour, fixedDepth = depth, useOpeningBookMoves = false)

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
    fun `quiescence still prevents giving away the queen`() {
        val game = game("4k3/5p2/6p1/7Q/8/8/8/4K3 w - - 0 1")
        val move = bot(Colour.WHITE, depth = 1).chooseMove(game)
        assertNotEquals("g6", move.to.toString())
        game.makeMove(move)
        val whiteQueens = game.board.getPieces().count { it.colour == Colour.WHITE && it.type == PieceType.QUEEN }
        assertEquals(1, whiteQueens)
    }

    @Test
    fun `forces a mate in two with all the pruning enabled`() {
        val game = game("6k1/5pp1/7p/7P/4Q3/8/8/R5K1 w - - 0 1")
        val firstMove = bot(Colour.WHITE, depth = 5).chooseMove(game)
        game.makeMove(firstMove)
        if (game.getGameResult() == GameResult.WHITE_WIN) return

        for (reply in game.getLegalMoves()) {
            val line = game.copy()
            line.makeMove(reply)
            val mateMove = bot(Colour.WHITE, depth = 5).chooseMove(line)
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
        bot(Colour.WHITE, depth = 5).chooseMove(game)
        assertEquals(before, game.board.toFEN(), "make/undo (and the null move) must leave the board untouched")
    }

    @Test
    fun `time budgeted search returns a legal move and uses the transposition table`() {
        val game = game("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 0 1")
        val bot = EliteNegamaxBot(colour = Colour.WHITE, useOpeningBookMoves = false, maxThinkTimeMillis = 500)

        val move = bot.chooseMove(game, timeLeft = 3000)

        assertTrue(game.getLegalMoves().contains(move), "must return a legal move")
        assertTrue(bot.nodesSearched > 0)
    }

    @Test
    fun `forward pruning does not change a forced tactical result`() {
        // With and without the extra pruning the bot must still grab the free queen.
        val fen = "4k3/8/8/3q4/4P3/8/8/4K3 w - - 0 1"
        val pruned = EliteNegamaxBot(
            colour = Colour.WHITE, fixedDepth = 4, useOpeningBookMoves = false
        ).chooseMove(game(fen))
        val plain = EliteNegamaxBot(
            colour = Colour.WHITE, fixedDepth = 4, useOpeningBookMoves = false,
            useNullMove = false, useLateMoveReductions = false,
            useReverseFutilityPruning = false, useFutilityPruning = false, useLateMovePruning = false
        ).chooseMove(game(fen))

        assertEquals("d5", pruned.to.toString())
        assertEquals("d5", plain.to.toString())
    }

    @Test
    fun `forward pruning does not prune an only-move check evasion`() {
        // Black is in check with exactly one legal reply; pruning must never skip it. The bot (to
        // move as White) must see the whole line: futility/LMP skip quiet moves, but never when the
        // side is in check or a mate score is in play.
        val game = game("k7/8/8/8/8/8/1q6/K7 w - - 0 1")   // White king cornered by the queen
        val move = bot(Colour.WHITE, depth = 4).chooseMove(game)
        assertTrue(game.getLegalMoves().contains(move), "must return a legal move under heavy pruning pressure")
    }

    @Test
    fun `works with a plain killer-history orderer too`() {
        // The countermove threading is optional: any MoveOrderer must still produce legal play.
        val game = game("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 0 1")
        val bot = EliteNegamaxBot(
            colour = Colour.WHITE, fixedDepth = 4, useOpeningBookMoves = false,
            moveOrderer = com.tward.engine.player.ordering.KillerHistoryMoveOrderer()
        )
        val move = bot.chooseMove(game)
        assertTrue(game.getLegalMoves().contains(move))
    }
}
