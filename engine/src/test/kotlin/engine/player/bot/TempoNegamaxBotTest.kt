package engine.player.bot

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.game.ChessGame
import com.tward.engine.game.GameResult
import com.tward.engine.player.bot.TempoNegamaxBot
import com.tward.engine.player.evaluator.NnueEvaluator
import engine.player.evaluator.nnue.TestNetworks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TempoNegamaxBotTest {

    private fun game(fen: String) = ChessGame(Board.fromFEN(fen))

    // The deterministic material network keeps these tests independent of whichever trained
    // network happens to be bundled.
    private fun bot(colour: Colour, depth: Int) = TempoNegamaxBot(
        colour = colour, fixedDepth = depth, useOpeningBookMoves = false,
        evaluator = NnueEvaluator(TestNetworks.material())
    )

    /** Exposes the protected budget hook so the time policy can be asserted directly. */
    private class ExposedTempo(
        useSmartTime: Boolean = true,
        increment: Int = 0
    ) : TempoNegamaxBot(
        colour = Colour.WHITE, useOpeningBookMoves = false,
        evaluator = NnueEvaluator(TestNetworks.material()), useSmartTime = useSmartTime
    ) {
        init {
            incrementMillis = increment
        }

        fun budget(game: ChessGame, timeLeft: Int) = chooseThinkTime(game, timeLeft)
    }

    private val middlegame =
        game("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 0 1")

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
    fun `plays a legal move under a running clock`() {
        val bot = TempoNegamaxBot(
            colour = Colour.WHITE, useOpeningBookMoves = false,
            evaluator = NnueEvaluator(TestNetworks.material())
        )
        val move = bot.chooseMove(middlegame, timeLeft = 2_000)

        assertTrue(middlegame.getLegalMoves().contains(move), "must return a legal move")
        assertTrue(bot.nodesSearched > 0)
    }

    @Test
    fun `the increment raises the budget`() {
        val without = ExposedTempo(increment = 0).budget(middlegame, 60_000)
        val with = ExposedTempo(increment = 2_000).budget(middlegame, 60_000)

        assertTrue(with > without, "increment time must be spent, not banked forever ($with vs $without)")
    }

    @Test
    fun `the hard budget never exceeds a third of the remaining clock`() {
        val budget = ExposedTempo(increment = 5_000).budget(middlegame, 1_200)
        assertTrue(budget <= 400, "must not risk flagging on a low clock (got $budget)")
    }

    @Test
    fun `the hard budget respects maxThinkTimeMillis`() {
        val budget = ExposedTempo().budget(middlegame, 600_000)
        assertTrue(budget <= 20_000, "the constructor cap must bound the hard budget (got $budget)")
    }

    @Test
    fun `smart time off reproduces the inherited flat budget`() {
        val budget = ExposedTempo(useSmartTime = false).budget(middlegame, 30_000)
        assertEquals(1_000, budget, "with smart time off the 1/30th rule must apply unchanged")
    }
}
