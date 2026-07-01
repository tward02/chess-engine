package com.tward.engine.player.bot

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.Move
import com.tward.engine.board.PieceType
import com.tward.engine.game.ChessGame
import com.tward.engine.player.evaluator.CompactEvaluator
import com.tward.engine.player.evaluator.Evaluator
import com.tward.engine.player.ordering.CounterMoveOrderer
import com.tward.engine.player.ordering.MoveOrderer
import kotlin.math.abs
import kotlin.math.ln

/**
 * [AdvancedNegamaxBot] plus the forward-pruning techniques that cut the tree hardest: instead of
 * searching every move to the same depth, positions the static evaluation already shows to be
 * hopeless (or clearly winning) are settled cheaply, buying extra depth on the lines that matter.
 * Everything the advanced bot does (TT, null move, SEE, aspiration windows, quiescence, check
 * extensions, PVS, opening book) is kept; only the per-node search and the clock policy change.
 *
 * - **Reverse futility pruning**: at shallow depth, if the static eval beats beta by a growing
 *   margin, the node is cut immediately — the opponent would never steer into it.
 * - **Futility pruning**: near the leaves, quiet moves are skipped when the static eval plus a
 *   margin cannot reach alpha — a quiet move rarely swings the score by hundreds of centipawns.
 * - **Late move pruning**: at shallow depth, quiet moves late in a well-ordered list are not
 *   searched at all; if none of the first dozen-odd moves worked, the stragglers won't either.
 * - **Log-formula late move reductions**: replaces the parent's flat 1–2 ply reduction with
 *   `ln(depth)·ln(moveIndex)` scaling, reducing far more aggressively deep in the tree.
 * - **Countermove ordering**: the default [CounterMoveOrderer] remembers which quiet move refuted
 *   each opponent move and tries it early; better ordering makes all the pruning above safer.
 * - **Adaptive time management**: when the best move has been stable across several iterations and
 *   most of the budget is spent, the search stops early and banks the rest for later moves.
 *
 * All pruning skips only moves the search has evidence are irrelevant and never mate-distance
 * scores, so tactics are preserved (see the toggle-equivalence tests). Each technique is a
 * constructor toggle for A/B measurement. Construct one per game.
 */
open class EliteNegamaxBot(
    colour: Colour,
    maxDepth: Int = 64,
    fixedDepth: Int? = null,
    useOpeningBookMoves: Boolean = true,
    maxBookMoves: Int = 16,
    maxThinkTimeMillis: Int = 20_000,
    evaluator: Evaluator = CompactEvaluator(),
    moveOrderer: MoveOrderer = CounterMoveOrderer(),
    private val useNullMove: Boolean = true,
    private val useLateMoveReductions: Boolean = true,
    useStaticExchangePruning: Boolean = true,
    useAspirationWindows: Boolean = true,
    private val useReverseFutilityPruning: Boolean = true,
    private val useFutilityPruning: Boolean = true,
    private val useLateMovePruning: Boolean = true,
    private val useAdaptiveTime: Boolean = true,
    transpositionTableBits: Int = 20,
    bookName: String = "/moveBook/Book.txt"
) : AdvancedNegamaxBot(
    colour, maxDepth, fixedDepth, useOpeningBookMoves, maxBookMoves, maxThinkTimeMillis,
    evaluator, moveOrderer,
    useNullMove = useNullMove,
    useLateMoveReductions = useLateMoveReductions,
    useStaticExchangePruning = useStaticExchangePruning,
    useAspirationWindows = useAspirationWindows,
    // The parent's TT backs its negamax, which this class overrides entirely; size it at two
    // entries so its arrays cost nothing. This bot's own table is sized below.
    transpositionTableBits = 1,
    bookName = bookName
) {

    // Only the CounterMoveOrderer needs the previous-move context threaded through the search;
    // any other MoveOrderer still works, just without the countermove band.
    private val counterOrderer = moveOrderer as? CounterMoveOrderer

    private val ttSize = 1 shl transpositionTableBits
    private val ttIndexMask = (ttSize - 1).toLong()
    private val ttKey = LongArray(ttSize)
    private val ttDepth = IntArray(ttSize)
    private val ttValue = IntArray(ttSize)
    private val ttFlag = ByteArray(ttSize)
    private val ttBestMove = arrayOfNulls<Move>(ttSize)

    // lmrTable[depth][moveIndex]; both axes clamped to 63
    private val lmrTable = Array(MAX_PLY) { depth ->
        IntArray(MAX_PLY) { moveIndex ->
            if (depth < 1 || moveIndex < 1) 0
            else (LMR_BASE + ln(depth.toDouble()) * ln(moveIndex.toDouble()) / LMR_DIVISOR).toInt()
        }
    }

    // Adaptive time state, reset at each search's first iteration
    private var searchStartNanos = 0L
    private var budgetNanos = Long.MAX_VALUE
    private var stableIterations = 0
    private var lastIterationBest: Move? = null

    override fun negamax(game: ChessGame, depth: Int, alpha: Int, beta: Int, ply: Int): Int {
        nodesSearched++

        if (timeUp()) {
            searchAborted = true
            return DRAW
        }

        if (game.isInsufficientMaterial() || game.isFiftyMoveRule() ||
            (game.board.halfMoveClock >= 8 && game.isThreefoldRepetition())
        ) {
            return drawScore(game.board.activeColour)
        }

        if (ply >= MAX_PLY) return quiesce(game, alpha, beta, ply)

        val alphaOrig = alpha
        var currentAlpha = alpha
        val isPvNode = beta - alpha > 1

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

        // The move that led to this node — the countermove context children must be restored to.
        val entryPrev = counterOrderer?.previousMove

        // Static eval drives the forward-pruning heuristics; unreliable in check, so unused there.
        val staticScore = if (inCheck) 0 else staticEval(game)

        // --- Reverse futility pruning ---
        if (useReverseFutilityPruning && !isPvNode && !inCheck &&
            searchDepth <= RFP_MAX_DEPTH && abs(beta) < MATE_BOUND &&
            staticScore - RFP_MARGIN * searchDepth >= beta
        ) {
            return staticScore
        }

        // --- Null-move pruning (parent's scheme, with a depth-scaled reduction) ---
        if (useNullMove && !inCheck && searchDepth >= NULL_MIN_DEPTH &&
            beta < MATE_BOUND && hasNonPawnMaterial(game.board, sideToMove)
        ) {
            val savedEnPassant = game.board.enPassantTarget
            game.board.enPassantTarget = null
            game.board.activeColour = sideToMove.opposite()
            counterOrderer?.previousMove = null   // no move to answer behind a pass

            val reduction = NULL_REDUCTION + searchDepth / NULL_DEPTH_DIVISOR
            val nullScore = -negamax(game, searchDepth - 1 - reduction, -beta, -beta + 1, ply + 1)

            counterOrderer?.previousMove = entryPrev
            game.board.activeColour = sideToMove
            game.board.enPassantTarget = savedEnPassant

            if (searchAborted) return DRAW
            // Don't trust a "mate" found behind a pass; only prune on a normal fail-high.
            if (nullScore >= beta && nullScore < MATE_BOUND) return nullScore
        }

        val legalMoves = game.getLegalMoves()
        if (legalMoves.isEmpty()) {
            return if (inCheck) -(MATE - ply) else drawScore(game.board.activeColour)
        }

        val orderedMoves = orderWithHashMove(moveOrderer.order(legalMoves, ply), hashMove)

        val lmpThreshold = if (useLateMovePruning && !isPvNode && !inCheck && searchDepth <= LMP_MAX_DEPTH) {
            LMP_BASE + searchDepth * searchDepth
        } else {
            Int.MAX_VALUE
        }

        var best = -INFINITY
        var bestMove: Move? = null
        var moveIndex = 0
        var searchedMoves = 0

        for (move in orderedMoves) {
            val isQuiet = move.capturedPiece == null && move.promotionType == null

            // Forward-prune hopeless quiet moves — never the first move searched (a best move must
            // exist) and never around mate scores (every escape must be examined).
            if (isQuiet && searchedMoves > 0 && abs(currentAlpha) < MATE_BOUND && best > -MATE_BOUND) {
                if (moveIndex >= lmpThreshold) {
                    moveIndex++
                    continue
                }
                if (useFutilityPruning && !isPvNode && !inCheck && searchDepth <= FUTILITY_MAX_DEPTH &&
                    staticScore + FUTILITY_MARGIN * searchDepth <= currentAlpha
                ) {
                    moveIndex++
                    continue
                }
            }

            game.makeMove(move)
            counterOrderer?.previousMove = move

            val score: Int
            if (searchedMoves == 0) {
                score = -negamax(game, searchDepth - 1, -beta, -currentAlpha, ply + 1)
            } else {
                val reduction = if (
                    useLateMoveReductions && isQuiet && !inCheck &&
                    searchDepth >= LMR_MIN_DEPTH && moveIndex >= LMR_MIN_MOVE_INDEX && move != hashMove
                ) lateMoveReduction(searchDepth, moveIndex, isPvNode) else 0

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

            counterOrderer?.previousMove = entryPrev
            game.undoMove(move)
            if (searchAborted) return best

            moveIndex++
            searchedMoves++
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
        if (depth == 1) {
            searchStartNanos = System.nanoTime()
            budgetNanos = deadlineNanos - searchStartNanos
            stableIterations = 0
            lastIterationBest = null
        }

        val result = super.searchToDepth(game, depth, previousBest, previousScore)

        // When the choice has been stable for several completed iterations and most of the budget is
        // gone, another (deeper, pricier) iteration is unlikely to change it or to finish — stop and
        // bank the remaining time for later in the game. budget check skips untimed searches.
        if (useAdaptiveTime && !searchAborted && budgetNanos < Long.MAX_VALUE / 2) {
            if (result.first == lastIterationBest) stableIterations++ else stableIterations = 0
            lastIterationBest = result.first

            val elapsed = System.nanoTime() - searchStartNanos
            if (depth >= STOP_MIN_DEPTH && stableIterations >= STOP_STABLE_ITERATIONS &&
                elapsed >= (budgetNanos * STOP_BUDGET_FRACTION).toLong()
            ) {
                deadlineNanos = System.nanoTime()   // the next iteration aborts immediately
            }
        }

        return result
    }

    /**
     * The score of a drawn line (repetition, 50-move, insufficient material, stalemate) for
     * [sideToMove]. Base returns [DRAW] (0); a subclass can return a contempt-shaded value so the
     * bot fights on in equal positions instead of settling. Time-abort sentinels are unaffected.
     */
    protected open fun drawScore(sideToMove: Colour): Int = DRAW

    private fun lateMoveReduction(depth: Int, moveIndex: Int, isPvNode: Boolean): Int {
        var reduction = lmrTable[depth.coerceAtMost(MAX_PLY - 1)][moveIndex.coerceAtMost(MAX_PLY - 1)]
        if (isPvNode) reduction--   // trust PV nodes more: reduce them less
        // Never reduce into quiescence: leave the child at least one ply of real search.
        return reduction.coerceAtMost(depth - 2).coerceAtLeast(0)
    }

    private fun hasNonPawnMaterial(board: Board, colour: Colour): Boolean {
        return board.getPieces().any {
            it.colour == colour && it.type != PieceType.PAWN && it.type != PieceType.KING
        }
    }

    private fun orderWithHashMove(ordered: List<Move>, hashMove: Move?): List<Move> {
        if (hashMove == null || ordered.firstOrNull() == hashMove || hashMove !in ordered) {
            return ordered
        }
        return listOf(hashMove) + (ordered - hashMove)
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
        const val NULL_DEPTH_DIVISOR = 6

        const val RFP_MAX_DEPTH = 6
        const val RFP_MARGIN = 80          // centipawns per remaining ply

        const val FUTILITY_MAX_DEPTH = 4
        const val FUTILITY_MARGIN = 120    // centipawns per remaining ply

        const val LMP_MAX_DEPTH = 4
        const val LMP_BASE = 3             // quiet-move quota is LMP_BASE + depth²

        const val LMR_MIN_DEPTH = 3
        const val LMR_MIN_MOVE_INDEX = 3
        const val LMR_BASE = 0.75
        const val LMR_DIVISOR = 2.25

        const val STOP_MIN_DEPTH = 6
        const val STOP_STABLE_ITERATIONS = 3
        const val STOP_BUDGET_FRACTION = 0.6
    }
}
