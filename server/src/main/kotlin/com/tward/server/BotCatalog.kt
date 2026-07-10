package com.tward.server

import com.tward.engine.board.Colour
import com.tward.engine.player.ChessBot
import com.tward.engine.player.bot.*
import com.tward.engine.player.evaluator.*
import com.tward.engine.player.ordering.CounterMoveOrderer
import com.tward.engine.player.ordering.KillerHistoryMoveOrderer
import com.tward.engine.player.ordering.MoveOrderer
import com.tward.engine.player.ordering.MvvLvaMoveOrderer
import com.tward.engine.player.ordering.NoOpMoveOrderer
import com.tward.shared.BotInfo

/** Which search algorithm a bot uses. */
enum class BotType { RANDOM, MINIMAX, ITERATIVE, NEGAMAX, ADVANCED, ELITE, APEX, NEURAL, TEMPO }

enum class EvaluatorType { BASIC, STANDARD, ADAPTIVE, POSITIONAL, COMPACT, ADVANCED, CONVERSION, NNUE }

enum class OrdererType { NONE, MVV_LVA, KILLER_HISTORY, COUNTER_MOVE }

/**
 * A complete, data-only description of an opponent bot: display info plus the engine knobs (search
 * type, evaluator, move ordering, depth/time). Being pure data, the whole catalog can move into a
 * database later with no code change — only [BotFactory] knows how to turn a spec into a live bot.
 */
data class BotSpec(
    val id: String,
    val name: String,
    val approxElo: Int,
    val description: String,
    val style: String,
    val type: BotType,
    val evaluator: EvaluatorType = EvaluatorType.COMPACT,
    val orderer: OrdererType = OrdererType.KILLER_HISTORY,
    val aggression: Int = 10,
    val depth: Int = 4,                    // fixed search depth for MINIMAX / max depth for ITERATIVE
    val maxThinkTimeMillis: Int = 1_000,   // time budget for the time-managed bots (NEGAMAX/ADVANCED/ITERATIVE)
    val useOpeningBook: Boolean = true
) {
    fun toInfo(): BotInfo = BotInfo(id, name, approxElo, description, style)
}

/**
 * The list of bots players can challenge. Hard-coded for now (the user's plan is to move these into a
 * DB); add as many as you like — every (bot type × evaluator × orderer × depth/time) combination is a
 * different opponent. Ordered weakest-to-strongest for display.
 */
object BotCatalog {

    val specs: List<BotSpec> = listOf(
        BotSpec(
            "randall", "Randall the Random", 250, "Plays a legal move completely at random.",
            "Chaos", BotType.RANDOM, useOpeningBook = false
        ),
        BotSpec(
            "milo", "Mini Milo", 500, "Looks one move ahead and grabs whatever it can.",
            "Beginner", BotType.MINIMAX, evaluator = EvaluatorType.BASIC, orderer = OrdererType.NONE, depth = 1
        ),
        BotSpec(
            "greedy-gail", "Greedy Gail", 800, "Counts material two moves deep — loves a free pawn.",
            "Materialist", BotType.MINIMAX, evaluator = EvaluatorType.BASIC, orderer = OrdererType.MVV_LVA, depth = 2
        ),
        BotSpec(
            "casual-carla", "Casual Carla", 1000, "A gentle club opponent that develops sensibly.",
            "All-rounder", BotType.MINIMAX, evaluator = EvaluatorType.STANDARD, depth = 2
        ),
        BotSpec(
            "tactical-tom", "Tactical Tom", 1250, "Three moves deep with capture-first ordering — watch your pieces.",
            "Tactical", BotType.MINIMAX, evaluator = EvaluatorType.STANDARD, orderer = OrdererType.MVV_LVA, depth = 3
        ),
        BotSpec(
            "aggressive-alex", "Aggressive Alex", 1300, "Throws pieces at your king; high aggression weighting.",
            "Aggressive", BotType.MINIMAX, evaluator = EvaluatorType.STANDARD, depth = 3, aggression = 35
        ),
        BotSpec(
            "steady-eddie", "Steady Eddie", 1450, "Iterative deepening with a tapered evaluation.",
            "Solid", BotType.ITERATIVE, evaluator = EvaluatorType.ADAPTIVE, depth = 6, maxThinkTimeMillis = 800
        ),
        BotSpec(
            "positional-paula", "Positional Paula", 1550, "Negamax with a rich positional eye for structure.",
            "Positional", BotType.NEGAMAX, evaluator = EvaluatorType.POSITIONAL, maxThinkTimeMillis = 700
        ),
        BotSpec(
            "speedy-gonzales", "Speedy Gonzales", 1500, "Blitzes moves out in a fifth of a second.",
            "Blitz", BotType.NEGAMAX, evaluator = EvaluatorType.COMPACT, maxThinkTimeMillis = 200
        ),
        BotSpec(
            "compact-casey", "Compact Casey", 1700, "Quiescence + transposition-free fast search.",
            "All-rounder", BotType.NEGAMAX, evaluator = EvaluatorType.COMPACT, maxThinkTimeMillis = 800
        ),
        BotSpec(
            "bishop-bella", "Bishop-Pair Bella", 1750, "Negamax that values mobility and king safety.",
            "Positional", BotType.NEGAMAX, evaluator = EvaluatorType.ADVANCED, maxThinkTimeMillis = 900
        ),
        BotSpec(
            "endgame-edna", "Endgame Edna", 1900, "Full modern search (TT, null-move, LMR, SEE).",
            "Endgame", BotType.ADVANCED, evaluator = EvaluatorType.COMPACT, maxThinkTimeMillis = 800
        ),
        BotSpec(
            "calculating-cal", "Calculating Cal", 2050, "The advanced engine with two seconds to think.",
            "Tactical", BotType.ADVANCED, evaluator = EvaluatorType.COMPACT, maxThinkTimeMillis = 2_000
        ),
        BotSpec(
            "grandmaster-greg", "Grandmaster Greg", 2250, "The strongest setup with a long think.",
            "Strongest", BotType.ADVANCED, evaluator = EvaluatorType.COMPACT, maxThinkTimeMillis = 4_000
        ),
        BotSpec(
            "elite-elena", "Elite Elena", 2350,
            "Futility pruning, late-move pruning and countermove ordering on top of the full advanced search.",
            "Strongest", BotType.ELITE, orderer = OrdererType.COUNTER_MOVE, maxThinkTimeMillis = 4_000
        ),
        BotSpec(
            "apex-alexei", "Apex Alexei", 2400,
            "The elite search plus contempt and endgame conversion — hates draws and grinds wins out.",
            "Relentless", BotType.APEX, evaluator = EvaluatorType.CONVERSION,
            orderer = OrdererType.COUNTER_MOVE, maxThinkTimeMillis = 4_000
        ),
        BotSpec(
            "neural-nina", "Neural Nina", 2450,
            "The apex search with a neural-network evaluation trained on the engine's own games.",
            "Learned", BotType.NEURAL, evaluator = EvaluatorType.NNUE,
            orderer = OrdererType.COUNTER_MOVE, maxThinkTimeMillis = 4_000
        ),
        BotSpec(
            "tempo-tara", "Tempo Tara", 2500,
            "The neural bot with grandmaster time management — banks time on easy moves and digs in when the position turns critical.",
            "Clock-wise", BotType.TEMPO, evaluator = EvaluatorType.NNUE,
            orderer = OrdererType.COUNTER_MOVE, maxThinkTimeMillis = 4_000
        )
    )

    private val byId: Map<String, BotSpec> = specs.associateBy { it.id }

    fun get(id: String): BotSpec? = byId[id]

    fun infos(): List<BotInfo> = specs.map { it.toInfo() }
}

/** Builds a live [ChessBot] from a [BotSpec]. The one place that maps catalog data to engine code. */
object BotFactory {

    fun build(spec: BotSpec, colour: Colour): ChessBot = when (spec.type) {
        BotType.RANDOM -> RandomBot()

        BotType.MINIMAX -> MiniMaxBot(
            depth = spec.depth, colour = colour, aggression = spec.aggression,
            useOpeningBookMoves = spec.useOpeningBook, evaluator = evaluator(spec), moveOrderer = orderer(spec)
        )

        BotType.ITERATIVE -> MiniMaxIterativeDeepeningBot(
            maxDepth = spec.depth, colour = colour, aggression = spec.aggression,
            useOpeningBookMoves = spec.useOpeningBook, evaluator = evaluator(spec), moveOrderer = orderer(spec)
        )

        BotType.NEGAMAX -> NegamaxBot(
            colour = colour, useOpeningBookMoves = spec.useOpeningBook,
            maxThinkTimeMillis = spec.maxThinkTimeMillis, evaluator = evaluator(spec), moveOrderer = orderer(spec)
        )

        BotType.ADVANCED -> AdvancedNegamaxBot(
            colour = colour, useOpeningBookMoves = spec.useOpeningBook,
            maxThinkTimeMillis = spec.maxThinkTimeMillis, evaluator = evaluator(spec), moveOrderer = orderer(spec)
        )

        BotType.ELITE -> EliteNegamaxBot(
            colour = colour, useOpeningBookMoves = spec.useOpeningBook,
            maxThinkTimeMillis = spec.maxThinkTimeMillis, evaluator = evaluator(spec), moveOrderer = orderer(spec)
        )

        BotType.APEX -> ApexNegamaxBot(
            colour = colour, useOpeningBookMoves = spec.useOpeningBook,
            maxThinkTimeMillis = spec.maxThinkTimeMillis, evaluator = evaluator(spec), moveOrderer = orderer(spec)
        )

        BotType.NEURAL -> NeuralNegamaxBot(
            colour = colour, useOpeningBookMoves = spec.useOpeningBook,
            maxThinkTimeMillis = spec.maxThinkTimeMillis, evaluator = evaluator(spec), moveOrderer = orderer(spec)
        )

        BotType.TEMPO -> TempoNegamaxBot(
            colour = colour, useOpeningBookMoves = spec.useOpeningBook,
            maxThinkTimeMillis = spec.maxThinkTimeMillis, evaluator = evaluator(spec), moveOrderer = orderer(spec)
        )
    }

    // Fresh instances per bot — evaluators/orderers may hold per-search state and aren't thread-safe.
    private fun evaluator(spec: BotSpec): Evaluator = when (spec.evaluator) {
        EvaluatorType.BASIC -> BasicEvaluator()
        EvaluatorType.STANDARD -> StandardEvaluator(aggression = spec.aggression)
        EvaluatorType.ADAPTIVE -> AdaptiveEvaluator(aggression = spec.aggression)
        EvaluatorType.POSITIONAL -> PositionalEvaluator(aggression = spec.aggression)
        EvaluatorType.COMPACT -> CompactEvaluator()
        EvaluatorType.ADVANCED -> AdvancedEvaluator()
        EvaluatorType.CONVERSION -> EndgameConversionEvaluator()
        EvaluatorType.NNUE -> NnueEvaluator()
    }

    private fun orderer(spec: BotSpec): MoveOrderer = when (spec.orderer) {
        OrdererType.NONE -> NoOpMoveOrderer
        OrdererType.MVV_LVA -> MvvLvaMoveOrderer()
        OrdererType.KILLER_HISTORY -> KillerHistoryMoveOrderer()
        OrdererType.COUNTER_MOVE -> CounterMoveOrderer()
    }
}
