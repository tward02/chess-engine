package com.tward.engine.player.bot

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.Move
import com.tward.engine.board.PieceType
import com.tward.engine.game.ChessGame
import com.tward.engine.player.evaluator.CompactEvaluator
import com.tward.engine.player.evaluator.Evaluator
import com.tward.engine.player.ordering.KillerHistoryMoveOrderer
import com.tward.engine.player.ordering.MoveOrderer
import kotlin.math.abs

/**
 * The strongest bot: [NegamaxBot] plus the three search techniques that buy the most depth per node,
 * which is where playing strength against rated opponents comes from. Everything [NegamaxBot] does
 * (quiescence, check extensions, PVS, iterative deepening, time management, opening book) is reused
 * unchanged — only the per-node search is overridden.
 *
 * - **Transposition table** ([Zobrist]-keyed): positions reached by different move orders are scored
 *   once. A usable stored bound returns immediately (a cutoff), and the stored best move is searched
 *   first, which is the single biggest ordering improvement. Mate scores are stored relative to the
 *   node so they stay correct when probed at a different distance from the root.
 * - **Null-move pruning**: if passing the move (a "null move") still fails high at reduced depth, the
 *   real position is almost certainly winning, so the subtree is pruned. Disabled in check, near the
 *   leaves, when the side to move has only pawns (zugzwang), and around mate scores.
 * - **Late move reductions**: late, quiet moves are searched shallower; if one unexpectedly beats
 *   alpha it is re-searched at full depth, so nothing is missed — only confirmed-dull lines run cheap.
 * - **Static exchange evaluation** prunes losing captures in quiescence (see [shouldPruneCapture]).
 * - **Aspiration windows**: each deepening iteration searches a narrow window around the last score,
 *   widening only on a fail — fewer nodes when the score is stable.
 *
 * It defaults to the fast [CompactEvaluator] and follows the opening book deeper than [NegamaxBot].
 * (The richer [com.tward.engine.player.evaluator.AdvancedEvaluator] — mobility + king safety — can be
 * passed in, but at fast time controls its higher per-node cost loses more depth than it gains, so it
 * is not the default; it may pay off at longer time controls.) Construct one per game.
 */
open class AdvancedNegamaxBot(
    colour: Colour,
    maxDepth: Int = 64,
    fixedDepth: Int? = null,
    useOpeningBookMoves: Boolean = true,
    maxBookMoves: Int = 16,
    maxThinkTimeMillis: Int = 20_000,
    evaluator: Evaluator = CompactEvaluator(),
    moveOrderer: MoveOrderer = KillerHistoryMoveOrderer(),
    private val useNullMove: Boolean = true,
    private val useLateMoveReductions: Boolean = true,
    private val useStaticExchangePruning: Boolean = true,
    private val useAspirationWindows: Boolean = true,
    transpositionTableBits: Int = 20
) : NegamaxBot(
    colour, maxDepth, fixedDepth, useOpeningBookMoves, maxBookMoves, maxThinkTimeMillis, evaluator, moveOrderer
) {

    // Transposition table as parallel primitive arrays (one slot per index; always-replace). The
    // full key is stored and verified on probe, so index collisions never return a wrong entry.
    private val ttSize = 1 shl transpositionTableBits
    private val ttIndexMask = (ttSize - 1).toLong()
    private val ttKey = LongArray(ttSize)
    private val ttDepth = IntArray(ttSize)
    private val ttValue = IntArray(ttSize)
    private val ttFlag = ByteArray(ttSize)
    private val ttBestMove = arrayOfNulls<Move>(ttSize)

    override fun negamax(game: ChessGame, depth: Int, alpha: Int, beta: Int, ply: Int): Int {
        nodesSearched++

        if (timeUp()) {
            searchAborted = true
            return DRAW
        }

        if (game.isInsufficientMaterial() || game.isFiftyMoveRule() ||
            (game.board.halfMoveClock >= 8 && game.isThreefoldRepetition())
        ) {
            return DRAW
        }

        if (ply >= MAX_PLY) return quiesce(game, alpha, beta, ply)

        val alphaOrig = alpha
        var currentAlpha = alpha

        // --- Transposition table probe ---
        val key = Zobrist.hash(game.board)
        val index = (key and ttIndexMask).toInt()
        var hashMove: Move? = null
        if (ttKey[index] == key) {
            hashMove = ttBestMove[index]
            if (ttDepth[index] >= depth) {
                val stored = fromTt(ttValue[index], ply)
                when (ttFlag[index].toInt()) {
                    FLAG_EXACT -> return stored
                    FLAG_LOWER -> if (stored >= beta) return stored
                    FLAG_UPPER -> if (stored <= alpha) return stored
                }
            }
        }

        val sideToMove = game.board.activeColour
        val inCheck = game.isInCheck(sideToMove)
        val searchDepth = if (inCheck) depth + 1 else depth   // check extension

        if (searchDepth <= 0) return quiesce(game, alpha, beta, ply)

        // --- Null-move pruning ---
        if (useNullMove && !inCheck && searchDepth >= NULL_MIN_DEPTH &&
            beta < MATE_BOUND && hasNonPawnMaterial(game.board, sideToMove)
        ) {
            val savedEnPassant = game.board.enPassantTarget
            game.board.enPassantTarget = null
            game.board.activeColour = sideToMove.opposite()

            val nullScore = -negamax(game, searchDepth - 1 - NULL_REDUCTION, -beta, -beta + 1, ply + 1)

            game.board.activeColour = sideToMove
            game.board.enPassantTarget = savedEnPassant

            if (searchAborted) return DRAW
            // Don't trust a "mate" found behind a pass; only prune on a normal fail-high.
            if (nullScore >= beta && nullScore < MATE_BOUND) return nullScore
        }

        val legalMoves = game.getLegalMoves()
        if (legalMoves.isEmpty()) {
            return if (inCheck) -(MATE - ply) else DRAW
        }

        val orderedMoves = orderWithHashMove(moveOrderer.order(legalMoves, ply), hashMove)

        var best = -INFINITY
        var bestMove: Move? = null
        var moveIndex = 0

        for (move in orderedMoves) {
            val isQuiet = move.capturedPiece == null && move.promotionType == null
            game.makeMove(move)

            val score: Int
            if (moveIndex == 0) {
                score = -negamax(game, searchDepth - 1, -beta, -currentAlpha, ply + 1)
            } else {
                val reduction = if (
                    useLateMoveReductions && isQuiet && !inCheck &&
                    searchDepth >= LMR_MIN_DEPTH && moveIndex >= LMR_MIN_MOVE_INDEX && move != hashMove
                ) lateMoveReduction(searchDepth, moveIndex) else 0

                var s = -negamax(game, searchDepth - 1 - reduction, -currentAlpha - 1, -currentAlpha, ply + 1)
                // A reduced move that beats alpha might be better than it looked: re-search full depth.
                if (!searchAborted && reduction > 0 && s > currentAlpha) {
                    s = -negamax(game, searchDepth - 1, -currentAlpha - 1, -currentAlpha, ply + 1)
                }
                // PVS re-search with the full window when the null window was breached.
                if (!searchAborted && s > currentAlpha && s < beta) {
                    s = -negamax(game, searchDepth - 1, -beta, -currentAlpha, ply + 1)
                }
                score = s
            }

            game.undoMove(move)
            if (searchAborted) return best

            moveIndex++
            if (score > best) {
                best = score
                bestMove = move
            }
            if (best > currentAlpha) currentAlpha = best
            if (currentAlpha >= beta) {
                moveOrderer.onBetaCutoff(move, ply, searchDepth)
                break
            }
        }

        // --- Transposition table store (always-replace) ---
        val flag = when {
            best <= alphaOrig -> FLAG_UPPER   // failed low: best is an upper bound
            best >= beta -> FLAG_LOWER        // failed high: best is a lower bound
            else -> FLAG_EXACT
        }
        ttKey[index] = key
        ttDepth[index] = searchDepth
        ttValue[index] = toTt(best, ply)
        ttFlag[index] = flag.toByte()
        ttBestMove[index] = bestMove

        return best
    }

    override fun searchToDepth(
        game: ChessGame,
        depth: Int,
        previousBest: Move?,
        previousScore: Int
    ): Pair<Move, Int> {
        // Shallow iterations, or when a mate is in view, are cheap and volatile — search them wide.
        if (!useAspirationWindows || depth < ASPIRATION_MIN_DEPTH || abs(previousScore) >= MATE_BOUND) {
            return searchRoot(game, depth, previousBest)
        }

        var delta = ASPIRATION_DELTA
        var alpha = previousScore - delta
        var beta = previousScore + delta

        while (true) {
            val result = searchRoot(game, depth, previousBest, alpha, beta)
            if (searchAborted) return result

            val score = result.second
            when {
                score <= alpha -> {                    // fail low: relax alpha, keep beta close
                    beta = (alpha + beta) / 2
                    alpha = score - delta
                }
                score >= beta -> beta = score + delta  // fail high: relax beta
                else -> return result
            }
            delta *= 2
            if (delta >= ASPIRATION_MAX_DELTA) {
                alpha = -INFINITY
                beta = INFINITY
            }
        }
    }

    override fun shouldPruneCapture(game: ChessGame, move: Move, lowerBound: Int, alpha: Int): Boolean {
        if (super.shouldPruneCapture(game, move, lowerBound, alpha)) return true
        // Skip captures that lose material outright; equal trades (SEE == 0) are still searched.
        if (!useStaticExchangePruning || move.promotionType != null || move.capturedPiece == null) return false
        return StaticExchangeEvaluator.evaluate(game.board, move) < 0
    }

    private fun orderWithHashMove(ordered: List<Move>, hashMove: Move?): List<Move> {
        if (hashMove == null || ordered.firstOrNull() == hashMove || hashMove !in ordered) {
            return ordered
        }
        return listOf(hashMove) + (ordered - hashMove)
    }

    private fun lateMoveReduction(depth: Int, moveIndex: Int): Int {
        val reduction = if (moveIndex >= LMR_DEEP_MOVE_INDEX && depth >= LMR_DEEP_DEPTH) 2 else 1
        // Never reduce into quiescence: leave the child at least one ply of real search.
        return reduction.coerceAtMost(depth - 2).coerceAtLeast(0)
    }

    private fun hasNonPawnMaterial(board: Board, colour: Colour): Boolean {
        return board.getPieces().any {
            it.colour == colour && it.type != PieceType.PAWN && it.type != PieceType.KING
        }
    }

    // Mate scores encode distance-from-root; the table is position-relative, so shift by ply on the
    // way in and back out (see NegamaxBot.MATE_BOUND).
    private fun toTt(value: Int, ply: Int): Int = when {
        value >= MATE_BOUND -> value + ply
        value <= -MATE_BOUND -> value - ply
        else -> value
    }

    private fun fromTt(value: Int, ply: Int): Int = when {
        value >= MATE_BOUND -> value - ply
        value <= -MATE_BOUND -> value + ply
        else -> value
    }

    private companion object {
        const val FLAG_EXACT = 0
        const val FLAG_LOWER = 1
        const val FLAG_UPPER = 2

        const val NULL_REDUCTION = 2
        const val NULL_MIN_DEPTH = 3

        const val LMR_MIN_DEPTH = 3
        const val LMR_MIN_MOVE_INDEX = 3
        const val LMR_DEEP_MOVE_INDEX = 6
        const val LMR_DEEP_DEPTH = 5

        const val ASPIRATION_MIN_DEPTH = 4
        const val ASPIRATION_DELTA = 30
        const val ASPIRATION_MAX_DELTA = 1000
    }
}
