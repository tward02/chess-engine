package com.tward.engine.player.bot

import com.tward.engine.board.Colour
import com.tward.engine.player.evaluator.EndgameConversionEvaluator
import com.tward.engine.player.evaluator.Evaluator
import com.tward.engine.player.ordering.CounterMoveOrderer
import com.tward.engine.player.ordering.MoveOrderer

/**
 * [EliteNegamaxBot] tuned to convert instead of draw. The elite search is reused unchanged; what
 * changes is how drawn and drawish positions are valued:
 *
 * - **Contempt**: a draw (repetition, 50-move, insufficient material, stalemate) scores
 *   -[contempt] centipawns for this bot's side instead of 0, so in an equal position it keeps
 *   playing for a win rather than shuffling into a repetition — while still accepting the draw
 *   when the alternative is at least [contempt] worse (it will bail into a perpetual rather than
 *   lose). Keep it modest: too high and the bot avoids draws it should take.
 * - **Conversion evaluation**: defaults to [EndgameConversionEvaluator], which adds mop-up scoring
 *   (drive the losing king to the edge, bring the winning king up) and fades the score as the
 *   50-move clock climbs so lines that make progress win the comparison.
 *
 * Both levers target the same failure: won or equal games drifting to draws. Everything else —
 * forward pruning, countermove ordering, adaptive time, the TT — is inherited. Construct one per game.
 */
open class ApexNegamaxBot(
    colour: Colour,
    maxDepth: Int = 64,
    fixedDepth: Int? = null,
    useOpeningBookMoves: Boolean = true,
    maxBookMoves: Int = 16,
    maxThinkTimeMillis: Int = 20_000,
    evaluator: Evaluator = EndgameConversionEvaluator(),
    moveOrderer: MoveOrderer = CounterMoveOrderer(),
    private val contempt: Int = 20,
    useNullMove: Boolean = true,
    useLateMoveReductions: Boolean = true,
    useStaticExchangePruning: Boolean = true,
    useAspirationWindows: Boolean = true,
    useReverseFutilityPruning: Boolean = true,
    useFutilityPruning: Boolean = true,
    useLateMovePruning: Boolean = true,
    useAdaptiveTime: Boolean = true,
    transpositionTableBits: Int = 20,
    bookName: String = "/moveBook/Book.txt"
) : EliteNegamaxBot(
    colour, maxDepth, fixedDepth, useOpeningBookMoves, maxBookMoves, maxThinkTimeMillis,
    evaluator, moveOrderer,
    useNullMove = useNullMove,
    useLateMoveReductions = useLateMoveReductions,
    useStaticExchangePruning = useStaticExchangePruning,
    useAspirationWindows = useAspirationWindows,
    useReverseFutilityPruning = useReverseFutilityPruning,
    useFutilityPruning = useFutilityPruning,
    useLateMovePruning = useLateMovePruning,
    useAdaptiveTime = useAdaptiveTime,
    transpositionTableBits = transpositionTableBits,
    bookName = bookName
) {

    override fun drawScore(sideToMove: Colour): Int =
        if (sideToMove == colour) -contempt else contempt
}
