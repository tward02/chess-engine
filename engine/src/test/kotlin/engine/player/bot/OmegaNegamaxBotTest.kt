package engine.player.bot

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.PieceType
import com.tward.engine.game.ChessGame
import com.tward.engine.game.GameResult
import com.tward.engine.player.bot.OmegaNegamaxBot
import com.tward.engine.player.evaluator.NnueEvaluator
import engine.player.evaluator.nnue.TestNetworks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class OmegaNegamaxBotTest {

    private fun game(fen: String) = ChessGame(Board.fromFEN(fen))

    // The deterministic material network keeps these tests independent of whichever trained
    // network happens to be bundled.
    private fun bot(colour: Colour, depth: Int) = OmegaNegamaxBot(
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
    fun `quiescence still prevents giving away the queen`() {
        val game = game("4k3/5p2/6p1/7Q/8/8/8/4K3 w - - 0 1")
        val move = bot(Colour.WHITE, depth = 1).chooseMove(game)
        assertNotEquals("g6", move.to.toString())
        game.makeMove(move)
        val whiteQueens = game.board.getPieces().count { it.colour == Colour.WHITE && it.type == PieceType.QUEEN }
        assertEquals(1, whiteQueens)
    }

    @Test
    fun `forces a mate in two with everything enabled`() {
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
        // Depth 8 reaches the singular-extension and IIR paths as well as the null move.
        bot(Colour.WHITE, depth = 8).chooseMove(game)
        assertEquals(before, game.board.toFEN(), "make/undo (and the null/singular searches) must leave the board untouched")
    }

    @Test
    fun `plays a legal move under a running clock`() {
        val game = game("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 0 1")
        val bot = OmegaNegamaxBot(
            colour = Colour.WHITE, useOpeningBookMoves = false,
            evaluator = NnueEvaluator(TestNetworks.material())
        )
        val move = bot.chooseMove(game, timeLeft = 2_000)

        assertTrue(game.getLegalMoves().contains(move), "must return a legal move")
        assertTrue(bot.nodesSearched > 0)
    }

    @Test
    fun `the new techniques do not change a forced tactical result`() {
        val fen = "4k3/8/8/3q4/4P3/8/8/4K3 w - - 0 1"
        val full = bot(Colour.WHITE, depth = 4).chooseMove(game(fen))
        val plain = OmegaNegamaxBot(
            colour = Colour.WHITE, fixedDepth = 4, useOpeningBookMoves = false,
            evaluator = NnueEvaluator(TestNetworks.material()),
            useSingularExtensions = false, useInternalIterativeReductions = false,
            useQuiescenceTT = false, useImprovingHeuristic = false, useSeeMovePruning = false
        ).chooseMove(game(fen))

        assertEquals("d5", full.to.toString())
        assertEquals("d5", plain.to.toString())
    }

    @Test
    fun `pruning does not lose an only-move check evasion`() {
        val game = game("k7/8/8/8/8/8/1q6/K7 w - - 0 1")   // White king cornered by the queen
        val move = bot(Colour.WHITE, depth = 4).chooseMove(game)
        assertTrue(game.getLegalMoves().contains(move), "must return a legal move under heavy pruning pressure")
    }

    @Test
    fun `scores an immediate repetition as a draw and keeps playing for more`() {
        // White is up a rook; shuffling back to the previous position must not look attractive.
        val game = game("6k1/8/8/8/8/8/R7/6K1 w - - 0 1")
        val bot = bot(Colour.WHITE, depth = 6)
        repeat(6) {
            if (game.getGameResult() != null) return
            val side = game.board.activeColour
            val move = if (side == Colour.WHITE) {
                bot.chooseMove(game)
            } else {
                game.getLegalMoves().first()
            }
            game.makeMove(move)
        }
        assertTrue(
            game.getGameResult() != GameResult.DRAW_THREEFOLD_REPETITION,
            "a winning position must not be shuffled into a repetition draw"
        )
    }

    @Test
    fun `deep search on a middlegame stays legal and restores state`() {
        val fen = "r2q1rk1/pp1bbppp/2n1pn2/2pp4/3P1B2/2NBPN2/PPP2PPP/R2Q1RK1 w - - 0 9"
        val game = game(fen)
        val before = game.board.toFEN()
        val move = bot(Colour.WHITE, depth = 8).chooseMove(game)
        assertTrue(game.getLegalMoves().contains(move))
        assertEquals(before, game.board.toFEN())
    }
}
