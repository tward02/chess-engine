package engine.nnue

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.game.ChessGame
import com.tward.engine.nnue.LabelConfig
import com.tward.engine.nnue.LongHashSet
import com.tward.engine.nnue.PositionLabeler
import com.tward.engine.nnue.SanParser
import com.tward.engine.player.bot.OmegaNegamaxBot
import com.tward.uci.UciMoveCodec
import kotlin.random.Random
import kotlin.test.*

class Phase1ToolsTest {

    private val middlegame = "r1bq1rk1/pp2bppp/2n1pn2/2pp4/3P4/2PBPN2/PP1N1PPP/R1BQ1RK1 w - - 0 8"

    private fun san(fen: String, san: String): String? =
        SanParser.findMove(ChessGame(Board.fromFEN(fen)), san)?.let(UciMoveCodec::encode)

    @Test
    fun `san resolves pawn, piece, capture, castling and promotion moves`() {
        val start = Board.getStartingBoard().toFEN()
        assertEquals("e2e4", san(start, "e4"))
        assertEquals("g1f3", san(start, "Nf3"))
        assertEquals("e1g1", san("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", "O-O"))
        assertEquals("e8c8", san("r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1", "O-O-O+"))
        assertEquals("e4d5", san("4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1", "exd5"))
        assertEquals("a7a8q", san("4k3/P7/8/8/8/8/8/4K3 w - - 0 1", "a8=Q+"))
        assertEquals("a7a8n", san("4k3/P7/8/8/8/8/8/4K3 w - - 0 1", "a8=N"))
    }

    @Test
    fun `san disambiguates by file and rank`() {
        // Knights on b1 and f1 can both reach d2; rooks on a1 and a5 can both reach a3.
        val fen = "4k3/8/8/R7/8/8/8/RN2KN2 w - - 0 1"
        assertEquals("b1d2", san(fen, "Nbd2"))
        assertEquals("f1d2", san(fen, "Nfd2"))
        assertNull(san(fen, "Nd2"), "ambiguous without a disambiguator")
        assertEquals("a1a3", san(fen, "R1a3"))
        assertEquals("a5a3", san(fen, "R5a3"))
    }

    @Test
    fun `san rejects illegal and malformed moves`() {
        val start = Board.getStartingBoard().toFEN()
        assertNull(san(start, "e5"))
        assertNull(san(start, "Qh5"))
        assertNull(san(start, ""))
        assertNull(san(start, "Z"))
    }

    @Test
    fun `node budget completes at least one iteration and is deterministic`() {
        fun search(limit: Int): Pair<String, Int> {
            val bot = OmegaNegamaxBot(Colour.WHITE, useOpeningBookMoves = false, transpositionTableBits = 16)
            bot.nodeLimit = limit
            val move = bot.chooseMove(ChessGame(Board.fromFEN(middlegame)), 0)
            assertEquals(move, bot.lastSearchMove)
            return UciMoveCodec.encode(move) to bot.nodesSearched
        }
        val (tinyMove, tinyNodes) = search(1)
        assertTrue(tinyMove.isNotEmpty() && tinyNodes > 0, "a 1-node budget still finishes depth 1")
        val (move, nodes) = search(20_000)
        assertTrue(nodes in 20_000..40_000, "stopped near the budget: $nodes")
        assertEquals(move to nodes, search(20_000), "same budget, same search")
    }

    @Test
    fun `labeler scores quiet positions from white's view and skips checks`() {
        val labeler = PositionLabeler(LabelConfig(bot = "omega", nodes = 5_000))
        val quiet = labeler.label(ChessGame(Board.fromFEN("4k3/8/8/8/8/8/8/Q3K3 b - - 0 1")))
        assertTrue((quiet.whiteScore ?: 0) > 300, "white is a queen up, even with black to move: ${quiet.whiteScore}")
        val check = labeler.label(ChessGame(Board.fromFEN("4k3/8/8/8/8/8/8/4K2r w - - 0 1")))
        assertNull(check.whiteScore, "in check: not a training position")
    }

    @Test
    fun `label config validates its bot`() {
        assertFailsWith<IllegalArgumentException> { LabelConfig(bot = "stockfish") }
        assertEquals("bot=omega nodes=20000 net=default", LabelConfig(bot = "omega", nodes = 20_000).describe())
    }

    @Test
    fun `long hash set matches a reference set`() {
        val set = LongHashSet(4)
        val reference = HashSet<Long>()
        val random = Random(7)
        repeat(50_000) {
            val key = if (it % 1000 == 0) 0L else random.nextLong(-5_000, 5_000) * 0x9E3779B97F4A7C15uL.toLong()
            assertEquals(reference.add(key), set.add(key))
        }
        assertEquals(reference.size, set.size)
        for (key in reference) assertTrue(key in set)
        assertFalse(123456789L in set && 123456789L !in reference)
    }
}
