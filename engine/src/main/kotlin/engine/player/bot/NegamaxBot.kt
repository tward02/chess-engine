package com.tward.engine.player.bot

import com.tward.engine.board.Colour
import com.tward.engine.board.Move
import com.tward.engine.board.PieceType
import com.tward.engine.game.ChessGame
import com.tward.engine.openingBook.OpeningBook
import com.tward.engine.player.ChessBot
import com.tward.engine.player.evaluator.CompactEvaluator
import com.tward.engine.player.evaluator.Evaluator
import com.tward.engine.player.ordering.KillerHistoryMoveOrderer
import com.tward.engine.player.ordering.MoveOrderer
import com.tward.engine.player.ordering.MvvLvaMoveOrderer
import com.tward.logging.Log
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/**
 * A tournament-strength bot. It searches with negamax + alpha-beta and adds the techniques the
 * simpler [MiniMaxBot] family lacks — the ones that stop an engine hanging pieces and let it see
 * far enough ahead to outplay opponents:
 *
 * - **Quiescence search** at the leaves: pending captures, promotions and check evasions are played
 *   out before scoring, so a position is only judged once it is quiet. This is the fix for the
 *   classic "captures into a defended square and loses its queen" blunder — the static leaf in a
 *   plain minimax can't see the recapture sitting one ply past the horizon.
 * - **Check extensions**: a side in check is searched one ply deeper so forcing lines aren't cut off.
 * - **Principal Variation Search**: after the first move, siblings are probed with a null window and
 *   only re-searched on a raise, which sharply cuts the tree given good move ordering.
 * - **Iterative deepening** with killer/history move ordering and the previous iteration's best move
 *   searched first, plus clock-aware time management so it uses its budget without flagging.
 *
 * The search reuses [Board.makeMove]/[undoMove] in place (no per-node copying) and generates legal
 * moves once per node. Scores are centipawns; mates are scored by distance so the bot prefers the
 * quickest win and the most distant loss.
 *
 * Construct one per game — [moveOrderer] holds per-search state. With [fixedDepth] set the bot
 * ignores the clock and always searches exactly that depth (deterministic, for tests).
 */
open class NegamaxBot(
    protected val colour: Colour,
    private val maxDepth: Int = 64,
    private val fixedDepth: Int? = null,
    private val useOpeningBookMoves: Boolean = true,
    private val maxBookMoves: Int = 5,
    private val maxThinkTimeMillis: Int = 20_000,
    protected val evaluator: Evaluator = CompactEvaluator(),
    protected val moveOrderer: MoveOrderer = KillerHistoryMoveOrderer()
) : ChessBot {

    private val log = Log.of<NegamaxBot>()

    private val openingBook = OpeningBook("/moveBook/Book.txt")
    private var numberOfOpeningMoves = 0

    // Captures/promotions in quiescence are ordered by MVV-LVA only; no killer/history there.
    protected val captureOrderer = MvvLvaMoveOrderer()

    var nodesSearched = 0
        protected set

    private var deadlineNanos = Long.MAX_VALUE
    protected var searchAborted = false

    override fun chooseMove(game: ChessGame, timeLeft: Int): Move {
        val legalMoves = game.getLegalMoves()
        require(legalMoves.isNotEmpty()) { "chooseMove called on a finished position ($colour to move)" }

        if (useOpeningBookMoves && numberOfOpeningMoves < maxBookMoves) {
            getBookMove(game, legalMoves)?.let { return it }
        }

        nodesSearched = 0
        searchAborted = false
        moveOrderer.reset()

        if (fixedDepth != null) {
            deadlineNanos = Long.MAX_VALUE
            return searchRoot(game, fixedDepth, previousBest = null).first
        }

        val thinkTime = chooseThinkTime(timeLeft)
        deadlineNanos = System.nanoTime() + thinkTime * 1_000_000L
        val startNanos = System.nanoTime()

        var bestMove = legalMoves.first()
        var bestScore = 0
        var previousBest: Move? = null
        var depthReached = 0

        for (depth in 1..maxDepth) {
            val (move, score) = searchToDepth(game, depth, previousBest, bestScore)
            if (searchAborted) break

            bestMove = move
            previousBest = move
            bestScore = score
            depthReached = depth

            log.debug {
                val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
                "$colour depth=$depth score=$score nodes=$nodesSearched in ${elapsedMs}ms"
            }

            // A forced mate has been found; no deeper search can improve on it.
            if (abs(score) >= MATE - MAX_PLY) break
        }

        log.info {
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
            "$colour chose ${bestMove.toAlgebraic()} depth=$depthReached score=$bestScore nodes=$nodesSearched total=${elapsedMs}ms think=${thinkTime}ms"
        }

        return bestMove
    }

    private fun getBookMove(game: ChessGame, legalMoves: List<Move>): Move? {
        numberOfOpeningMoves++
        val fen = game.board.toFEN(isFullFEN = false)

        openingBook.getBookMove(fen)?.moveStr?.let { bookMoveStr ->
            legalMoves.firstOrNull { it.toAlgebraic() == bookMoveStr }?.let {
                log.debug { "$colour played book move ${it.toAlgebraic()} (move $numberOfOpeningMoves)" }
                return it
            }
        }
        return null
    }

    // One iteration of iterative deepening. Base does a plain full-window root search; overridable so
    // a subclass can wrap it in aspiration windows around [previousScore].
    protected open fun searchToDepth(
        game: ChessGame,
        depth: Int,
        previousBest: Move?,
        previousScore: Int
    ): Pair<Move, Int> = searchRoot(game, depth, previousBest)

    // Root search for one depth, optionally inside an [alpha]/[beta] window (aspiration). Tracked
    // separately from negamax so the chosen move surfaces and the previous iteration's best can be
    // tried first. Returns (bestMove, score); on abort the caller discards it.
    protected open fun searchRoot(
        game: ChessGame,
        depth: Int,
        previousBest: Move?,
        alpha: Int = -INFINITY,
        beta: Int = INFINITY
    ): Pair<Move, Int> {
        val ordered = moveOrderer.order(game.getLegalMoves(), ply = 0)
        val moves = if (previousBest != null && previousBest in ordered) {
            listOf(previousBest) + (ordered - previousBest)
        } else {
            ordered
        }

        var bestMove = moves.first()
        var bestScore = -INFINITY
        var currentAlpha = alpha
        var first = true

        for (move in moves) {
            game.makeMove(move)
            val score = if (first) {
                -negamax(game, depth - 1, -beta, -currentAlpha, ply = 1)
            } else {
                var s = -negamax(game, depth - 1, -currentAlpha - 1, -currentAlpha, ply = 1)
                if (s > currentAlpha && s < beta) s = -negamax(game, depth - 1, -beta, -currentAlpha, ply = 1)
                s
            }
            game.undoMove(move)

            if (searchAborted) return Pair(bestMove, bestScore)

            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }
            if (score > currentAlpha) currentAlpha = score
            if (currentAlpha >= beta) break   // fail-high; only reachable inside an aspiration window
            first = false
        }

        return Pair(bestMove, bestScore)
    }

    protected open fun negamax(game: ChessGame, depth: Int, alpha: Int, beta: Int, ply: Int): Int {
        nodesSearched++

        if (timeUp()) {
            searchAborted = true
            return DRAW
        }

        // Draw detection (no move generation). Threefold needs a quiet run of plies, so the FEN
        // scan is gated on the halfmove clock to keep the common case cheap.
        if (game.isInsufficientMaterial() || game.isFiftyMoveRule() ||
            (game.board.halfMoveClock >= 8 && game.isThreefoldRepetition())
        ) {
            return DRAW
        }

        if (ply >= MAX_PLY) return quiesce(game, alpha, beta, ply)

        val inCheck = game.isInCheck(game.board.activeColour)
        val searchDepth = if (inCheck) depth + 1 else depth   // check extension

        if (searchDepth <= 0) return quiesce(game, alpha, beta, ply)

        val moves = moveOrderer.order(game.getLegalMoves(), ply)
        if (moves.isEmpty()) {
            // No legal moves: checkmate (scored by distance so faster mates win) or stalemate.
            return if (inCheck) -(MATE - ply) else DRAW
        }

        var best = -INFINITY
        var currentAlpha = alpha
        var first = true

        for (move in moves) {
            game.makeMove(move)
            val score = if (first) {
                -negamax(game, searchDepth - 1, -beta, -currentAlpha, ply + 1)
            } else {
                var s = -negamax(game, searchDepth - 1, -currentAlpha - 1, -currentAlpha, ply + 1)
                if (s > currentAlpha && s < beta) {
                    s = -negamax(game, searchDepth - 1, -beta, -currentAlpha, ply + 1)
                }
                s
            }
            game.undoMove(move)

            if (searchAborted) return best

            if (score > best) best = score
            if (best > currentAlpha) currentAlpha = best
            if (currentAlpha >= beta) {
                moveOrderer.onBetaCutoff(move, ply, searchDepth)
                break
            }
            first = false
        }

        return best
    }

    /**
     * Quiescence: extend the search through forcing moves until the position is quiet, so a leaf is
     * never scored in the middle of an exchange. When in check every evasion is searched (and a
     * static "stand pat" is not allowed); otherwise only captures and promotions are tried on top of
     * the stand-pat score, with delta pruning to skip captures that cannot lift alpha.
     */
    protected fun quiesce(game: ChessGame, alpha: Int, beta: Int, ply: Int): Int {
        nodesSearched++

        if (timeUp()) {
            searchAborted = true
            return DRAW
        }
        if (ply >= MAX_PLY) return staticEval(game)

        val inCheck = game.isInCheck(game.board.activeColour)

        var best: Int
        var currentAlpha = alpha
        val moves: List<Move>

        if (inCheck) {
            best = -INFINITY
            moves = game.getLegalMoves()
            if (moves.isEmpty()) return -(MATE - ply)   // checkmate found in quiescence
        } else {
            val standPat = staticEval(game)
            if (standPat >= beta) return standPat
            best = standPat
            if (standPat > currentAlpha) currentAlpha = standPat
            moves = game.getLegalMoves().filter { it.capturedPiece != null || it.promotionType != null }
            if (moves.isEmpty()) return standPat
        }

        for (move in captureOrderer.order(moves, ply = 0)) {
            if (!inCheck && shouldPruneCapture(game, move, best, currentAlpha)) continue

            game.makeMove(move)
            val score = -quiesce(game, -beta, -currentAlpha, ply + 1)
            game.undoMove(move)

            if (searchAborted) return best

            if (score > best) best = score
            if (best > currentAlpha) currentAlpha = best
            if (currentAlpha >= beta) break
        }

        return best
    }

    /**
     * Whether a quiescence capture is hopeless and can be skipped. Base applies delta pruning;
     * [AdvancedNegamaxBot] additionally prunes captures that lose material by static exchange.
     * Only consulted when the side to move is not in check (every move must be tried out of check).
     */
    protected open fun shouldPruneCapture(game: ChessGame, move: Move, lowerBound: Int, alpha: Int): Boolean {
        if (move.promotionType != null) return false
        val captured = move.capturedPiece ?: return false
        return lowerBound + material(captured.type) + DELTA_MARGIN <= alpha
    }

    /** Static evaluation from the side-to-move's perspective (negamax convention). */
    protected fun staticEval(game: ChessGame): Int {
        val whiteScore = evaluator.evaluate(game)
        return if (game.board.activeColour == Colour.WHITE) whiteScore else -whiteScore
    }

    protected fun timeUp(): Boolean {
        if (fixedDepth != null) return false
        return (nodesSearched and TIME_CHECK_MASK) == 0 && System.nanoTime() >= deadlineNanos
    }

    private fun chooseThinkTime(timeLeft: Int): Int {
        // Aim to spend ~1/30th of the remaining clock on this move, but never risk flagging.
        var thinkTime = timeLeft / 30.0
        thinkTime = minOf(thinkTime, maxThinkTimeMillis.toDouble())
        thinkTime = minOf(thinkTime, timeLeft * 0.9)
        val floor = minOf(50.0, timeLeft * 0.25)
        return ceil(max(floor, thinkTime)).toInt()
    }

    protected fun material(type: PieceType): Int = when (type) {
        PieceType.PAWN -> 100
        PieceType.KNIGHT -> 320
        PieceType.BISHOP -> 330
        PieceType.ROOK -> 500
        PieceType.QUEEN -> 900
        PieceType.KING -> 0
    }

    companion object {
        internal const val MAX_PLY = 64

        // INFINITY is kept well below Int.MAX_VALUE so negating alpha/beta bounds never overflows.
        internal const val INFINITY = 1_000_000_000
        internal const val MATE = 1_000_000
        internal const val DRAW = 0

        // Mate scores live within MAX_PLY of MATE; anything past this bound is a forced mate.
        internal const val MATE_BOUND = MATE - MAX_PLY

        private const val DELTA_MARGIN = 200
        private const val TIME_CHECK_MASK = 2047
    }
}