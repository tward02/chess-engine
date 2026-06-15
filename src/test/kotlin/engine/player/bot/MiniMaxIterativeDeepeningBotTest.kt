package engine.player.bot

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.Move
import com.tward.engine.game.ChessGame
import com.tward.engine.game.GameResult
import com.tward.engine.player.bot.MiniMaxIterativeDeepeningBot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MiniMaxIterativeDeepeningBotTest {

    private fun game(fen: String) = ChessGame(Board.fromFEN(fen))

    // A bot with the opening book off, so chooseMove always runs a real search (no book shortcut)
    private fun bot(
        maxDepth: Int,
        colour: Colour = Colour.WHITE,
        useMaxTime: Boolean = true
    ) = MiniMaxIterativeDeepeningBot(
        maxDepth = maxDepth,
        colour = colour,
        useOpeningBookMoves = false,
        useMaxTime = useMaxTime
    )

    private fun assertLegalMove(game: ChessGame, move: Move) {
        val legal = game.getLegalMoves().any {
            it.from.toString() == move.from.toString() && it.to.toString() == move.to.toString()
        }
        assertTrue(legal, "Expected $move to be legal in ${game.board.toFEN(isFullFEN = false)}")
    }

    // A budget big enough that the search is never the thing that stops a shallow tactic.
    // (It is also well into the range that the old Int overflow turned into a past deadline.)
    private val GENEROUS_TIME = 100_000

    // -- Tactical correctness: parity with the plain MiniMaxBot ----------------------------------

    @Test
    fun `white finds mate in one`() {

        // Back-rank mate: Ra8#
        val game = game("6k1/5ppp/8/8/8/8/8/R3K3 w - - 0 1")

        val move = bot(maxDepth = 3).chooseMove(game, GENEROUS_TIME)
        game.makeMove(move)

        assertEquals(GameResult.WHITE_WIN, game.getGameResult())
    }

    @Test
    fun `black finds mate in one`() {

        // Back-rank mate: Ra1#
        val game = game("r3k3/8/8/8/8/8/5PPP/6K1 b - - 0 1")

        val move = bot(maxDepth = 3, colour = Colour.BLACK).chooseMove(game, GENEROUS_TIME)
        game.makeMove(move)

        assertEquals(GameResult.BLACK_WIN, game.getGameResult())
    }

    @Test
    fun `white captures hanging queen`() {

        val game = game("4k3/8/8/3q4/4P3/8/8/4K3 w - - 0 1")

        val move = bot(maxDepth = 4).chooseMove(game, GENEROUS_TIME)

        assertEquals("e4", move.from.toString())
        assertEquals("d5", move.to.toString())
    }

    @Test
    fun `black captures hanging queen`() {

        val game = game("4k3/8/8/4p3/3Q4/8/8/4K3 b - - 0 1")

        val move = bot(maxDepth = 4, colour = Colour.BLACK).chooseMove(game, GENEROUS_TIME)

        assertEquals("e5", move.from.toString())
        assertEquals("d4", move.to.toString())
    }

    @Test
    fun `returns a legal move from the opening position`() {

        val game = game("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")

        val move = bot(maxDepth = 4).chooseMove(game, GENEROUS_TIME)

        assertLegalMove(game, move)
    }

    // -- Iterative deepening behaviour -----------------------------------------------------------

    @Test
    fun `searches deeper when given a higher max depth`() {

        // A quiet opening so the node count is driven by depth, not by a single forcing tactic.
        val fen = "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 0 1"

        // Generous time so the depth cap (not the clock) is what stops each search, keeping the
        // node counts deterministic.
        val shallow = bot(maxDepth = 1)
        val deep = bot(maxDepth = 3)

        shallow.chooseMove(game(fen), GENEROUS_TIME)
        deep.chooseMove(game(fen), GENEROUS_TIME)

        // If iterative deepening were broken and only ever ran depth 1 (the original
        // "return inside the loop" bug), these would be equal.
        assertTrue(
            deep.nodesSearched > shallow.nodesSearched,
            "deeper search should visit more nodes: deep=${deep.nodesSearched}, shallow=${shallow.nodesSearched}"
        )
    }

    @Test
    fun `does not abort before searching a single node with a large time budget`() {

        // Regression for the deadline integer overflow: `thinkTime * 1000000` was Int arithmetic,
        // so any think time above ~2147 ms wrapped negative and put the deadline in the past,
        // making every search abort on its first node and default to the first legal move.
        val game = game("6k1/5ppp/8/8/8/8/8/R3K3 w - - 0 1")

        val bot = bot(maxDepth = 4)
        // 200_000 ms produces a think time well past the old 32-bit overflow threshold.
        val move = bot.chooseMove(game, timeLeft = 200_000)

        assertTrue(bot.nodesSearched > 0, "bot aborted before searching any nodes (overflow regression)")

        game.makeMove(move)
        assertEquals(GameResult.WHITE_WIN, game.getGameResult())
    }

    @Test
    fun `still searches normally after a previous move ran out of time`() {

        // Regression for the missing `searchAborted = false` reset: once a search hit its deadline
        // the flag stayed set, so every later move bailed out and returned the first legal move.
        val bot = bot(maxDepth = 6)

        // Force an abort: a complex position with a 1 ms budget can't finish its deepening.
        val kiwipete = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"
        bot.chooseMove(game(kiwipete), timeLeft = 1)

        // The same bot must still find a forced mate when later given a real budget.
        val mate = game("6k1/5ppp/8/8/8/8/8/R3K3 w - - 0 1")
        val move = bot.chooseMove(mate, GENEROUS_TIME)

        assertTrue(bot.nodesSearched > 0, "bot stayed aborted across moves")
        mate.makeMove(move)
        assertEquals(GameResult.WHITE_WIN, mate.getGameResult())
    }

    @Test
    fun `a smaller time budget searches fewer nodes`() {

        val fen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"

        // Same depth ceiling for both, so the only difference is how long they are allowed to think.
        val tight = bot(maxDepth = 4)
        val relaxed = bot(maxDepth = 4)

        tight.chooseMove(game(fen), timeLeft = 1)
        relaxed.chooseMove(game(fen), GENEROUS_TIME)

        assertTrue(
            tight.nodesSearched < relaxed.nodesSearched,
            "a 1ms budget should cut the search short: tight=${tight.nodesSearched}, relaxed=${relaxed.nodesSearched}"
        )
    }

    @Test
    fun `returns a legal move when given zero time`() {

        // The tournament defaults to an untimed setting (initialTimeMillis = 0), which hands the
        // bot timeLeft = 0. It can't search, but it must still return a legal move rather than crash.
        val game = game("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")

        val move = bot(maxDepth = 6).chooseMove(game, timeLeft = 0)

        assertLegalMove(game, move)
    }
}
