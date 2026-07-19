package com.tward.engine.player.bot

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.Move
import com.tward.engine.board.PieceType
import com.tward.engine.game.ChessGame
import com.tward.engine.player.evaluator.Evaluator
import com.tward.engine.player.evaluator.NnueEvaluator
import com.tward.engine.player.ordering.CounterMoveOrderer
import com.tward.engine.player.ordering.MoveOrderer
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * The strongest bot: [TempoNegamaxBot]'s NNUE evaluation, contempt and smart clock with a rebuilt
 * per-node search adding the techniques the elite search lacks. Like [EliteNegamaxBot] before it,
 * the whole of [negamax] (and quiescence) is overridden; iterative deepening, aspiration windows
 * and the time policy are inherited unchanged.
 *
 * - **Singular extensions + multicut**: when the hash move's stored score towers over every
 *   alternative (verified by a reduced-depth search excluding it), that move is forced in spirit
 *   and is searched a ply deeper; when several moves beat beta instead, the node is cut outright.
 * - **Improving heuristic**: the static eval two plies ago says whether the line is trending our
 *   way; reverse futility, futility, late move pruning and LMR all prune harder in non-improving
 *   nodes and gentler in improving ones.
 * - **Internal iterative reductions**: a node with no hash move is searched one ply shallower —
 *   the depth is better spent once the TT knows the best move.
 * - **Quiescence TT**: capture sequences are memoised too; a quiescence node hit in an earlier
 *   line returns instantly.
 * - **SEE pruning in the main search**: at shallow depth, captures that lose material by static
 *   exchange are skipped entirely, not just in quiescence.
 * - **Mate distance pruning**: once a forced mate is known, lines that cannot beat it are cut.
 * - **Two-fold repetition scoring**: any position seen before in the game or search line scores as
 *   a (contempt-shaded) draw immediately — the opponent can force the threefold on demand.
 * - **Null move upgrades**: only tried when the static eval already beats beta, with a reduction
 *   that grows with both depth and eval surplus.
 * - **Generation-aware transposition table**: entries from earlier searches are replaced first,
 *   deep entries from the current search survive shallow overwrites.
 *
 * Draw-adjacent returns keep [ApexNegamaxBot]'s contempt via [drawScore]. Each new technique is a
 * constructor toggle for A/B measurement. Construct one per game.
 */
open class OmegaNegamaxBot(
    colour: Colour,
    maxDepth: Int = 64,
    fixedDepth: Int? = null,
    useOpeningBookMoves: Boolean = true,
    maxBookMoves: Int = 16,
    maxThinkTimeMillis: Int = 20_000,
    evaluator: Evaluator = NnueEvaluator(),
    moveOrderer: MoveOrderer = CounterMoveOrderer(),
    contempt: Int = 20,
    private val useNullMove: Boolean = true,
    private val useLateMoveReductions: Boolean = true,
    useStaticExchangePruning: Boolean = true,
    useAspirationWindows: Boolean = true,
    private val useReverseFutilityPruning: Boolean = true,
    private val useFutilityPruning: Boolean = true,
    private val useLateMovePruning: Boolean = true,
    private val useSingularExtensions: Boolean = true,
    private val useInternalIterativeReductions: Boolean = true,
    private val useQuiescenceTT: Boolean = true,
    private val useImprovingHeuristic: Boolean = true,
    private val useSeeMovePruning: Boolean = true,
    useSmartTime: Boolean = true,
    moveOverheadMillis: Int = 50,
    transpositionTableBits: Int = 20,
    bookName: String = "/moveBook/Book.txt"
) : TempoNegamaxBot(
    colour, maxDepth, fixedDepth, useOpeningBookMoves, maxBookMoves, maxThinkTimeMillis,
    evaluator, moveOrderer,
    contempt = contempt,
    useNullMove = useNullMove,
    useLateMoveReductions = useLateMoveReductions,
    useStaticExchangePruning = useStaticExchangePruning,
    useAspirationWindows = useAspirationWindows,
    useReverseFutilityPruning = useReverseFutilityPruning,
    useFutilityPruning = useFutilityPruning,
    useLateMovePruning = useLateMovePruning,
    useSmartTime = useSmartTime,
    moveOverheadMillis = moveOverheadMillis,
    // The parent's negamax (and so its TT) is overridden wholesale; two entries so its arrays
    // cost nothing. This bot's own table is sized below.
    transpositionTableBits = 1,
    bookName = bookName
) {

    private val counterOrderer = moveOrderer as? CounterMoveOrderer

    private val ttSize = 1 shl transpositionTableBits
    private val ttIndexMask = (ttSize - 1).toLong()
    private val ttKey = LongArray(ttSize)
    private val ttDepth = IntArray(ttSize)
    private val ttValue = IntArray(ttSize)
    private val ttFlag = ByteArray(ttSize)
    private val ttGeneration = ByteArray(ttSize)
    private val ttBestMove = arrayOfNulls<Move>(ttSize)
    private var generation = 0

    // lmrTable[depth][moveIndex]; both axes clamped to 63
    private val lmrTable = Array(MAX_PLY) { depth ->
        IntArray(MAX_PLY) { moveIndex ->
            if (depth < 1 || moveIndex < 1) 0
            else (LMR_BASE + ln(depth.toDouble()) * ln(moveIndex.toDouble()) / LMR_DIVISOR).toInt()
        }
    }

    // Per-ply search stacks: the static eval trail behind the improving heuristic, and the move a
    // singular verification search must exclude at its (re-entered) ply.
    private val evalStack = IntArray(MAX_PLY) { EVAL_NONE }
    private val excludedMove = arrayOfNulls<Move>(MAX_PLY)

    override fun searchToDepth(
        game: ChessGame,
        depth: Int,
        previousBest: Move?,
        previousScore: Int
    ): Pair<Move, Int> {
        if (depth == 1) {
            generation = (generation + 1) and 0x7F
            evalStack.fill(EVAL_NONE)
            excludedMove.fill(null)
        }
        return super.searchToDepth(game, depth, previousBest, previousScore)
    }

    override fun negamax(game: ChessGame, depth: Int, alpha: Int, beta: Int, ply: Int): Int {
        nodesSearched++

        if (timeUp()) {
            searchAborted = true
            return DRAW
        }

        val board = game.board
        if (game.isInsufficientMaterial() || game.isFiftyMoveRule() || game.isRepetition()) {
            return drawScore(board.activeColour)
        }

        if (ply >= MAX_PLY) return qsearch(game, alpha, beta, ply)

        // --- Mate distance pruning: cap the window by the best/worst mate still reachable ---
        var currentAlpha = max(alpha, -(MATE - ply))
        val cappedBeta = min(beta, MATE - ply - 1)
        if (currentAlpha >= cappedBeta) return currentAlpha

        val alphaOrig = currentAlpha
        val isPvNode = beta - alpha > 1
        val excluded = excludedMove[ply]

        // --- Transposition table probe (no cutoffs inside a singular verification search) ---
        val key = board.zobristKey
        val index = (key and ttIndexMask).toInt()
        var hashMove: Move? = null
        var ttHit = false
        var ttStoredValue = 0
        var ttStoredDepth = -1
        var ttStoredFlag = -1
        if (ttKey[index] == key) {
            ttHit = true
            hashMove = ttBestMove[index]
            ttStoredDepth = ttDepth[index]
            ttStoredFlag = ttFlag[index].toInt()
            ttStoredValue = fromTt(ttValue[index], ply)
            if (excluded == null && ttStoredDepth >= depth) {
                when (ttStoredFlag) {
                    FLAG_EXACT -> return ttStoredValue
                    FLAG_LOWER -> if (ttStoredValue >= cappedBeta) return ttStoredValue
                    FLAG_UPPER -> if (ttStoredValue <= currentAlpha) return ttStoredValue
                }
            }
        }

        val sideToMove = board.activeColour
        val inCheck = game.isInCheck(sideToMove)
        var searchDepth = if (inCheck) depth + 1 else depth   // check extension

        if (searchDepth <= 0) return qsearch(game, currentAlpha, cappedBeta, ply)

        // --- Internal iterative reduction: without a hash move this node orders blind; search it
        // shallower now and let the TT entry pay the depth back on the next visit.
        if (useInternalIterativeReductions && !inCheck && hashMove == null &&
            searchDepth >= IIR_MIN_DEPTH && excluded == null
        ) {
            searchDepth--
        }

        // The move that led to this node — the countermove context children must be restored to.
        val entryPrev = counterOrderer?.previousMove

        // Static eval drives the forward-pruning heuristics; unreliable in check, so unused there.
        val staticScore = if (inCheck) 0 else staticEval(game)
        evalStack[ply] = if (inCheck) EVAL_NONE else staticScore
        val improving = improving(ply, inCheck, staticScore)

        // --- Reverse futility pruning (margin shrinks when the line is improving) ---
        if (useReverseFutilityPruning && !isPvNode && !inCheck && excluded == null &&
            searchDepth <= RFP_MAX_DEPTH && abs(cappedBeta) < MATE_BOUND &&
            staticScore - RFP_MARGIN * (searchDepth - if (improving) 1 else 0) >= cappedBeta
        ) {
            return staticScore
        }

        // --- Null-move pruning: only from positions already standing above beta ---
        if (useNullMove && !inCheck && excluded == null && searchDepth >= NULL_MIN_DEPTH &&
            cappedBeta < MATE_BOUND && staticScore >= cappedBeta &&
            hasNonPawnMaterial(board, sideToMove)
        ) {
            val savedEnPassant = board.enPassantTarget
            board.enPassantTarget = null
            board.activeColour = sideToMove.opposite()
            counterOrderer?.previousMove = null   // no move to answer behind a pass

            val reduction = NULL_REDUCTION + searchDepth / NULL_DEPTH_DIVISOR +
                    min((staticScore - cappedBeta) / NULL_EVAL_DIVISOR, NULL_EVAL_MAX_BONUS)
            val nullScore = -negamax(game, searchDepth - 1 - reduction, -cappedBeta, -cappedBeta + 1, ply + 1)

            counterOrderer?.previousMove = entryPrev
            board.activeColour = sideToMove
            board.enPassantTarget = savedEnPassant

            if (searchAborted) return DRAW
            // Don't trust a "mate" found behind a pass; only prune on a normal fail-high.
            if (nullScore >= cappedBeta && nullScore < MATE_BOUND) return nullScore
        }

        val legalMoves = game.getLegalMoves()
        if (legalMoves.isEmpty()) {
            return if (inCheck) -(MATE - ply) else drawScore(sideToMove)
        }

        // --- Singular extension probe: is the hash move the only move that holds its score? ---
        var singularExtension = 0
        if (useSingularExtensions && excluded == null && depth >= SE_MIN_DEPTH &&
            hashMove != null && ttHit && ttStoredFlag != FLAG_UPPER &&
            ttStoredDepth >= depth - SE_TT_DEPTH_SLACK && abs(ttStoredValue) < MATE_BOUND
        ) {
            val singularBeta = ttStoredValue - SE_MARGIN_PER_DEPTH * depth
            excludedMove[ply] = hashMove
            val singularScore = negamax(game, (depth - 1) / 2, singularBeta - 1, singularBeta, ply)
            excludedMove[ply] = null
            if (searchAborted) return DRAW

            if (singularScore < singularBeta) {
                singularExtension = 1              // nothing else comes close: trust it deeper
            } else if (singularBeta >= cappedBeta && !isPvNode) {
                return singularBeta                // multicut: several moves already beat beta
            }
        }

        val orderedMoves = orderWithHashMove(moveOrderer.order(legalMoves, ply), hashMove)

        val lmpThreshold = if (useLateMovePruning && !isPvNode && !inCheck && searchDepth <= LMP_MAX_DEPTH) {
            (LMP_BASE + searchDepth * searchDepth) / (if (improving) 1 else 2)
        } else {
            Int.MAX_VALUE
        }
        val futilityMargin = FUTILITY_MARGIN * searchDepth + if (improving) FUTILITY_IMPROVING_BONUS else 0

        var best = -INFINITY
        var bestMove: Move? = null
        var moveIndex = 0
        var searchedMoves = 0

        for (move in orderedMoves) {
            if (move == excluded) continue

            val isQuiet = move.capturedPiece == null && move.promotionType == null

            // Forward-prune hopeless moves — never the first move searched (a best move must
            // exist) and never around mate scores (every escape must be examined).
            if (searchedMoves > 0 && abs(currentAlpha) < MATE_BOUND && best > -MATE_BOUND) {
                if (isQuiet) {
                    if (moveIndex >= lmpThreshold) {
                        moveIndex++
                        continue
                    }
                    if (useFutilityPruning && !isPvNode && !inCheck && searchDepth <= FUTILITY_MAX_DEPTH &&
                        staticScore + futilityMargin <= currentAlpha
                    ) {
                        moveIndex++
                        continue
                    }
                } else if (useSeeMovePruning && !isPvNode && searchDepth <= SEE_PRUNE_MAX_DEPTH &&
                    move.capturedPiece != null && move.promotionType == null &&
                    StaticExchangeEvaluator.evaluate(board, move) < -SEE_PRUNE_MARGIN * searchDepth
                ) {
                    moveIndex++
                    continue
                }
            }

            val extension = if (move == hashMove) singularExtension else 0

            game.makeMove(move)
            counterOrderer?.previousMove = move

            val score: Int
            if (searchedMoves == 0) {
                score = -negamax(game, searchDepth - 1 + extension, -cappedBeta, -currentAlpha, ply + 1)
            } else {
                val reduction = if (
                    useLateMoveReductions && isQuiet && !inCheck &&
                    searchDepth >= LMR_MIN_DEPTH && moveIndex >= LMR_MIN_MOVE_INDEX && move != hashMove
                ) lateMoveReduction(searchDepth, moveIndex, isPvNode, improving) else 0

                var s = -negamax(game, searchDepth - 1 - reduction, -currentAlpha - 1, -currentAlpha, ply + 1)
                // A reduced move that beats alpha might be better than it looked: re-search full depth.
                if (!searchAborted && reduction > 0 && s > currentAlpha) {
                    s = -negamax(game, searchDepth - 1, -currentAlpha - 1, -currentAlpha, ply + 1)
                }
                // PVS re-search with the full window when the null window was breached.
                if (!searchAborted && s > currentAlpha && s < cappedBeta) {
                    s = -negamax(game, searchDepth - 1, -cappedBeta, -currentAlpha, ply + 1)
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
            if (currentAlpha >= cappedBeta) {
                moveOrderer.onBetaCutoff(move, ply, searchDepth)
                break
            }
        }

        // Inside a singular verification the only legal move can be the excluded one; failing low
        // is exactly the signal the caller wants.
        if (searchedMoves == 0) return currentAlpha

        if (excluded == null) {
            val flag = when {
                best <= alphaOrig -> FLAG_UPPER   // failed low: best is an upper bound
                best >= cappedBeta -> FLAG_LOWER  // failed high: best is a lower bound
                else -> FLAG_EXACT
            }
            ttStore(index, key, searchDepth, toTt(best, ply), flag, bestMove)
        }

        return best
    }

    /**
     * Quiescence with a transposition table: same forcing-move resolution as
     * [NegamaxBot.quiesce], but positions resolved once are never resolved again.
     */
    private fun qsearch(game: ChessGame, alpha: Int, beta: Int, ply: Int): Int {
        nodesSearched++

        if (timeUp()) {
            searchAborted = true
            return DRAW
        }
        if (ply >= MAX_PLY) return staticEval(game)

        val key = game.board.zobristKey
        val index = (key and ttIndexMask).toInt()
        if (useQuiescenceTT && ttKey[index] == key) {
            val stored = fromTt(ttValue[index], ply)
            when (ttFlag[index].toInt()) {
                FLAG_EXACT -> return stored
                FLAG_LOWER -> if (stored >= beta) return stored
                FLAG_UPPER -> if (stored <= alpha) return stored
            }
        }

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

        var bestMove: Move? = null
        for (move in captureOrderer.order(moves, ply = 0)) {
            if (!inCheck && shouldPruneCapture(game, move, best, currentAlpha)) continue

            game.makeMove(move)
            val score = -qsearch(game, -beta, -currentAlpha, ply + 1)
            game.undoMove(move)

            if (searchAborted) return best

            if (score > best) {
                best = score
                bestMove = move
            }
            if (best > currentAlpha) currentAlpha = best
            if (currentAlpha >= beta) break
        }

        if (useQuiescenceTT) {
            val flag = when {
                best >= beta -> FLAG_LOWER
                best <= alpha -> FLAG_UPPER
                else -> FLAG_EXACT
            }
            ttStore(index, key, 0, toTt(best, ply), flag, bestMove)
        }

        return best
    }

    // Replace entries from earlier searches freely; within a search, keep the deeper analysis
    // unless the new entry is exact (a completed PV node is the most valuable thing in the table).
    private fun ttStore(index: Int, key: Long, depth: Int, value: Int, flag: Int, move: Move?) {
        val sameKey = ttKey[index] == key
        if (sameKey || ttGeneration[index].toInt() != generation ||
            depth >= ttDepth[index] || flag == FLAG_EXACT
        ) {
            ttKey[index] = key
            ttDepth[index] = depth
            ttValue[index] = value
            ttFlag[index] = flag.toByte()
            ttGeneration[index] = generation.toByte()
            // A best-move-less refresh of the same position keeps the known best move; a new
            // position must never inherit another position's move.
            if (move != null || !sameKey) ttBestMove[index] = move
        }
    }

    // Improving: the static eval is better than our side's eval two plies up the stack (four as a
    // fallback when we were in check then). Unknown history counts as improving — prune gentler.
    private fun improving(ply: Int, inCheck: Boolean, staticScore: Int): Boolean {
        if (!useImprovingHeuristic) return true
        if (inCheck) return false
        if (ply >= 2 && evalStack[ply - 2] != EVAL_NONE) return staticScore > evalStack[ply - 2]
        if (ply >= 4 && evalStack[ply - 4] != EVAL_NONE) return staticScore > evalStack[ply - 4]
        return true
    }

    private fun lateMoveReduction(depth: Int, moveIndex: Int, isPvNode: Boolean, improving: Boolean): Int {
        var reduction = lmrTable[depth.coerceAtMost(MAX_PLY - 1)][moveIndex.coerceAtMost(MAX_PLY - 1)]
        if (isPvNode) reduction--             // trust PV nodes more: reduce them less
        if (!improving && !isPvNode) reduction++   // sinking lines earn less depth
        // Never reduce into quiescence: leave the child at least one ply of real search.
        return reduction.coerceAtMost(depth - 2).coerceAtLeast(0)
    }

    private fun hasNonPawnMaterial(board: Board, colour: Colour): Boolean {
        var found = false
        board.forEachPiece { _, _, piece ->
            if (piece.colour == colour && piece.type != PieceType.PAWN && piece.type != PieceType.KING) {
                found = true
            }
        }
        return found
    }

    private fun orderWithHashMove(ordered: List<Move>, hashMove: Move?): List<Move> {
        if (hashMove == null) return ordered
        val at = ordered.indexOf(hashMove)
        if (at <= 0) return ordered
        val result = ArrayList<Move>(ordered.size)
        result.add(hashMove)
        for (i in ordered.indices) {
            if (i != at) result.add(ordered[i])
        }
        return result
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

        const val EVAL_NONE = Int.MIN_VALUE

        const val NULL_REDUCTION = 3
        const val NULL_MIN_DEPTH = 3
        const val NULL_DEPTH_DIVISOR = 5
        const val NULL_EVAL_DIVISOR = 200   // extra reduction per 200cp of eval above beta...
        const val NULL_EVAL_MAX_BONUS = 2   // ...capped here

        const val RFP_MAX_DEPTH = 6
        const val RFP_MARGIN = 80           // centipawns per remaining ply

        const val FUTILITY_MAX_DEPTH = 4
        const val FUTILITY_MARGIN = 120     // centipawns per remaining ply
        const val FUTILITY_IMPROVING_BONUS = 60

        const val LMP_MAX_DEPTH = 4
        const val LMP_BASE = 3              // quiet-move quota is LMP_BASE + depth², halved if not improving

        const val LMR_MIN_DEPTH = 3
        const val LMR_MIN_MOVE_INDEX = 3
        const val LMR_BASE = 0.75
        const val LMR_DIVISOR = 2.25

        const val IIR_MIN_DEPTH = 4

        const val SE_MIN_DEPTH = 7
        const val SE_TT_DEPTH_SLACK = 3     // TT entry must be from within this many plies of here
        const val SE_MARGIN_PER_DEPTH = 3   // singular beta sits this far below the TT score per ply

        const val SEE_PRUNE_MAX_DEPTH = 5
        const val SEE_PRUNE_MARGIN = 100    // a capture may lose up to this per remaining ply
    }
}
