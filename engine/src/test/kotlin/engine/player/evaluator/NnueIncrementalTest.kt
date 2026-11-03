package engine.player.evaluator

import com.tward.engine.bench.Benchmark
import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.Move
import com.tward.engine.game.ChessGame
import com.tward.engine.player.evaluator.NnueEvaluator
import com.tward.engine.player.evaluator.nnue.NnueFeatures
import com.tward.engine.player.evaluator.nnue.NnueNetwork
import com.tward.engine.player.ordering.MvvLvaMoveOrderer
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NnueIncrementalTest {

    private val network = NnueNetwork.default()

    /** The pre-quantisation float evaluator, kept here as the reference the integer one must match. */
    private fun floatEval(board: Board): Int {
        val hidden = network.hiddenSize
        val white = network.ftBias.copyOf()
        val black = network.ftBias.copyOf()
        board.forEachPiece { col, row, piece ->
            val w = NnueFeatures.index(Colour.WHITE, piece.colour, piece.type, col, row)
            val b = NnueFeatures.mirror(w)
            for (h in 0 until hidden) {
                white[h] += network.ftWeights[w * hidden + h]
                black[h] += network.ftWeights[b * hidden + h]
            }
        }
        val whiteToMove = board.activeColour == Colour.WHITE
        val own = if (whiteToMove) white else black
        val opp = if (whiteToMove) black else white
        var sum = network.outBias
        for (h in 0 until hidden) {
            sum += network.outWeights[h] * own[h].coerceIn(0f, 1f) + network.outWeights[hidden + h] * opp[h].coerceIn(0f, 1f)
        }
        val score = (sum * NnueNetwork.OUTPUT_SCALE).roundToInt().coerceIn(-30_000, 30_000)
        return if (whiteToMove) score else -score
    }

    private val startPositions = listOf(
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        "r3k2r/pPppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",   // castling, promotions
        "rnbqkbnr/ppp1p1pp/8/3pPp2/8/8/PPPP1PPP/RNBQKBNR w KQkq f6 0 3",          // en passant
        "8/2P5/8/8/8/8/5p2/K6k w - - 0 1"                                        // promotions both sides
    )

    @Test
    fun `incremental scores equal a from-scratch rebuild through random play and undo`() {
        val random = Random(2026)
        val reference = NnueEvaluator(network)
        var checked = 0
        for (fen in startPositions) repeat(6) {
            val game = ChessGame(Board.fromFEN(fen))
            val evaluator = NnueEvaluator(network)
            val played = ArrayList<Move>()
            repeat(120) {
                val moves = game.getLegalMoves()
                // Mostly forward, sometimes back, so undo paths (captures restored, castling undone,
                // promotions demoted) are exercised as often as make paths.
                if (moves.isEmpty() || (played.isNotEmpty() && random.nextInt(4) == 0)) {
                    if (played.isEmpty()) return@repeat
                    game.undoMove(played.removeAt(played.size - 1))
                } else {
                    val captures = moves.filter { it.capturedPiece != null || it.promotionType != null }
                    val move = if (captures.isNotEmpty() && random.nextBoolean()) captures.random(random) else moves.random(random)
                    game.makeMove(move)
                    played += move
                }
                assertEquals(reference.evaluateFromScratch(game), evaluator.evaluate(game), game.board.toFEN())
                checked++
            }
        }
        assertTrue(checked > 2000)
    }

    @Test
    fun `quantised scores match the float network to within a centipawn`() {
        val evaluator = NnueEvaluator(network)
        var worst = 0
        for (fen in Benchmark.POSITIONS + startPositions) {
            val game = ChessGame(Board.fromFEN(fen))
            for (move in game.getLegalMoves()) {
                game.makeMove(move)
                worst = maxOf(worst, abs(evaluator.evaluate(game) - floatEval(game.board)))
                game.undoMove(move)
            }
        }
        assertTrue(worst <= 1, "worst difference from the float network: $worst cp")
    }

    @Test
    fun `two evaluators sharing a board both stay correct`() {
        // Two NNUE bots playing each other share one board but it has one listener slot.
        val game = ChessGame(Board.fromFEN(startPositions[1]))
        val a = NnueEvaluator(network)
        val b = NnueEvaluator(network)
        val reference = NnueEvaluator(network)
        val random = Random(7)
        repeat(60) {
            val moves = game.getLegalMoves()
            if (moves.isEmpty()) return@repeat
            game.makeMove(moves.random(random))
            val expected = reference.evaluateFromScratch(game)
            assertEquals(expected, a.evaluate(game))
            assertEquals(expected, b.evaluate(game))
        }
    }

    @Test
    fun `moving to another board or clearing it never leaves stale accumulators`() {
        val evaluator = NnueEvaluator(network)
        val reference = NnueEvaluator(network)
        val first = ChessGame(Board.fromFEN(startPositions[0]))
        val second = ChessGame(Board.fromFEN(startPositions[1]))
        evaluator.evaluate(first)
        assertEquals(reference.evaluateFromScratch(second), evaluator.evaluate(second))
        assertEquals(null, first.board.listener, "the old board must be released")

        // The old board changing must not disturb the evaluator now attached to the new one.
        first.makeMove(first.getLegalMoves().first())
        assertEquals(reference.evaluateFromScratch(second), evaluator.evaluate(second))

        second.board.clearBoard()
        Board.fromFEN("4k3/8/8/8/8/8/8/Q3K3 w - - 0 1").forEachPiece { col, row, piece ->
            second.board.setPiece(com.tward.engine.board.Square(col, row), piece)
        }
        assertEquals(reference.evaluateFromScratch(second), evaluator.evaluate(second))
    }

    @Test
    fun `legal move generation leaves the board listener in place`() {
        val evaluator = NnueEvaluator(network)
        val game = ChessGame(Board.fromFEN(startPositions[1]))
        evaluator.evaluate(game)
        val listener = game.board.listener
        game.getLegalMoves()
        assertSame(listener, game.board.listener)
    }

    @Test
    fun `move ordering keeps sortedByDescending's order, ties included`() {
        val game = ChessGame(Board.fromFEN(startPositions[1]))
        val moves = game.getLegalMoves()
        val orderer = MvvLvaMoveOrderer()
        assertEquals(moves.sortedByDescending { orderer.scoreOf(it) }, orderer.order(moves, 0))
    }
}
