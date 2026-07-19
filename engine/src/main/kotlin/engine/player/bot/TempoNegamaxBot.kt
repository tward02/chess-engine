package com.tward.engine.player.bot

import com.tward.engine.board.Colour
import com.tward.engine.board.Move
import com.tward.engine.game.ChessGame
import com.tward.engine.player.ClockAware
import com.tward.engine.player.evaluator.Evaluator
import com.tward.engine.player.evaluator.NnueEvaluator
import com.tward.engine.player.ordering.CounterMoveOrderer
import com.tward.engine.player.ordering.MoveOrderer
import kotlin.math.ceil
import kotlin.math.max

/**
 * [NeuralNegamaxBot]'s search with grandmaster time management. The inherited scheme spends a flat
 * 1/30th of the clock per move; this bot decides *how long a move deserves*:
 *
 * - **Soft/hard budgets**: each move gets a soft target of `clock / movesToGo` (movesToGo shrinks
 *   as the game progresses, so the spend rate rises) and a hard ceiling of several times that.
 *   The search deadline is the hard ceiling; whether to start another iteration is decided at the
 *   soft target.
 * - **Increment awareness** ([ClockAware]): most of the per-move increment is added to the soft
 *   budget — on increment time controls the inherited bots leave that time unspent forever.
 * - **Instability extension**: when the best move changes between iterations, or the score drops
 *   sharply, the position is exactly where extra thought converts to Elo — the soft target scales
 *   up (bounded by the hard ceiling). A search that keeps confirming the same move stops well
 *   before the soft target and banks the difference, like the parent's adaptive stop but budgeted
 *   per-position rather than per-flat-slice.
 * - **Move overhead**: a fixed margin per move is reserved for transport latency (network play),
 *   so the bot cannot flag on lag it never sees.
 *
 * With [useSmartTime] off the bot budgets exactly like [NeuralNegamaxBot] (the parent's adaptive
 * stop is re-enabled), so an A/B run isolates the time policy alone. Untimed and [fixedDepth]
 * searches are unaffected. Construct one per game.
 */
open class TempoNegamaxBot(
    colour: Colour,
    maxDepth: Int = 64,
    fixedDepth: Int? = null,
    useOpeningBookMoves: Boolean = true,
    maxBookMoves: Int = 16,
    maxThinkTimeMillis: Int = 20_000,
    evaluator: Evaluator = NnueEvaluator(),
    moveOrderer: MoveOrderer = CounterMoveOrderer(),
    contempt: Int = 20,
    useNullMove: Boolean = true,
    useLateMoveReductions: Boolean = true,
    useStaticExchangePruning: Boolean = true,
    useAspirationWindows: Boolean = true,
    useReverseFutilityPruning: Boolean = true,
    useFutilityPruning: Boolean = true,
    useLateMovePruning: Boolean = true,
    private val useSmartTime: Boolean = true,
    private val moveOverheadMillis: Int = 50,
    transpositionTableBits: Int = 20,
    bookName: String = "/moveBook/Book.txt"
) : NeuralNegamaxBot(
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
    // Smart time subsumes the parent's flat adaptive stop; with smart time off the parent scheme
    // is restored so the bot degrades to exactly NeuralNegamaxBot for A/B baselines.
    useAdaptiveTime = !useSmartTime,
    transpositionTableBits = transpositionTableBits,
    bookName = bookName
), ClockAware {

    override var incrementMillis = 0

    // The hard ceiling is inherited via maxThinkTimeMillis, but the parent keeps it private.
    private val hardCapMillis = maxThinkTimeMillis

    // Per-search smart-time state, set in chooseThinkTime / reset at each search's first iteration.
    private var smartSearchActive = false
    private var softBudgetNanos = Long.MAX_VALUE
    private var searchStart = 0L
    private var instability = 0.0
    private var lastBest: Move? = null
    private var lastScore = 0

    override fun chooseThinkTime(game: ChessGame, timeLeft: Int): Int {
        smartSearchActive = useSmartTime && timeLeft > 0
        if (!smartSearchActive) return super.chooseThinkTime(game, timeLeft)

        val usable = max(timeLeft - moveOverheadMillis, 1)
        val movesToGo = max(MTG_MIN, MTG_BASE - game.board.fullMoveNumber)
        val soft = usable.toDouble() / movesToGo + incrementMillis * INCREMENT_FRACTION
        val hard = minOf(soft * HARD_FACTOR, usable / 3.0, hardCapMillis.toDouble()).coerceAtLeast(1.0)

        softBudgetNanos = (minOf(soft, hard).coerceAtLeast(1.0) * 1_000_000).toLong()
        return ceil(hard).toInt()
    }

    override fun searchToDepth(
        game: ChessGame,
        depth: Int,
        previousBest: Move?,
        previousScore: Int
    ): Pair<Move, Int> {
        if (depth == 1) {
            searchStart = System.nanoTime()
            instability = 0.0
            lastBest = null
            lastScore = 0
        }

        val result = super.searchToDepth(game, depth, previousBest, previousScore)

        if (smartSearchActive && !searchAborted) {
            if (depth >= CONTROL_MIN_DEPTH) {
                val changed = result.first != lastBest
                val dropped = lastScore - result.second >= SCORE_DROP_CP
                instability = instability * INSTABILITY_DECAY +
                        (if (changed) 1.0 else 0.0) + (if (dropped) DROP_WEIGHT else 0.0)

                // Stable searches stop below the soft target and bank the difference; unstable ones
                // earn more, up to the hard deadline already governing the search.
                val allowance = softBudgetNanos * (STABLE_SCALE + INSTABILITY_SCALE * instability)
                if (System.nanoTime() - searchStart >= allowance) {
                    deadlineNanos = System.nanoTime()   // the next iteration aborts immediately
                }
            }
            lastBest = result.first
            lastScore = result.second
        }

        return result
    }

    private companion object {
        const val MTG_BASE = 40            // movesToGo estimate at move 1...
        const val MTG_MIN = 14             // ...never dropping below this later on
        const val INCREMENT_FRACTION = 0.75
        const val HARD_FACTOR = 4.0        // hard ceiling as a multiple of the soft target

        const val CONTROL_MIN_DEPTH = 5    // no stop decisions before this iteration
        const val SCORE_DROP_CP = 30
        const val INSTABILITY_DECAY = 0.65
        const val DROP_WEIGHT = 0.5
        const val STABLE_SCALE = 0.65      // fully stable: stop at 65% of the soft target
        const val INSTABILITY_SCALE = 0.55 // each recent best-move change buys ~55% more
    }
}
