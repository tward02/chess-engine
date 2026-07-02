package com.tward.engine.player.bot

import com.tward.engine.board.Colour
import com.tward.engine.player.evaluator.Evaluator
import com.tward.engine.player.evaluator.NnueEvaluator
import com.tward.engine.player.ordering.CounterMoveOrderer
import com.tward.engine.player.ordering.MoveOrderer

/**
 * [ApexNegamaxBot]'s search with a learned evaluation: the handcrafted evaluator is replaced by
 * [NnueEvaluator], a neural network trained on the engine's own self-play games (the data generator
 * and trainer live in `com.tward.engine.nnue`). Everything else — the elite forward pruning,
 * countermove ordering, adaptive time management and contempt — is inherited unchanged, so an A/B
 * run against [ApexNegamaxBot] isolates exactly one variable: the evaluation function.
 *
 * The network it plays with is whatever is bundled at `resources/nnue/default.nnue`; retraining and
 * replacing that file upgrades this bot without touching code. Construct one per game.
 */
open class NeuralNegamaxBot(
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
    useAdaptiveTime: Boolean = true,
    transpositionTableBits: Int = 20,
    bookName: String = "/moveBook/Book.txt"
) : ApexNegamaxBot(
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
    useAdaptiveTime = useAdaptiveTime,
    transpositionTableBits = transpositionTableBits,
    bookName = bookName
)
