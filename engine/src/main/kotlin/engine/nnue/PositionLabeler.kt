package com.tward.engine.nnue

import com.tward.engine.board.Colour
import com.tward.engine.board.Move
import com.tward.engine.game.ChessGame
import com.tward.engine.player.bot.AdvancedNegamaxBot
import com.tward.engine.player.bot.EliteNegamaxBot
import com.tward.engine.player.bot.NegamaxBot
import com.tward.engine.player.bot.NeuralNegamaxBot
import com.tward.engine.player.bot.OmegaNegamaxBot
import com.tward.engine.player.evaluator.NnueEvaluator
import com.tward.engine.player.evaluator.nnue.NnueNetwork
import java.nio.file.Paths
import kotlin.math.abs

/**
 * How training positions are scored: which bot searches ([bot]), with which net for the NNUE bots
 * ([net]), and for how long, either a fixed [depth] or a [nodes] budget per position (preferred: it
 * gives every position comparable effort, and the deeper bots then search deeper for free).
 */
data class LabelConfig(
    val bot: String = "advanced",
    val depth: Int = 4,
    val nodes: Int? = null,
    val net: String = "default"
) {
    init {
        require(bot in BOTS) { "unknown label bot '$bot' (use ${BOTS.joinToString()})" }
        require(nodes == null || nodes > 0) { "nodes must be positive" }
    }

    fun describe(): String = "bot=$bot " + (nodes?.let { "nodes=$it" } ?: "depth=$depth") +
            (if (bot in NNUE_BOTS) " net=$net" else "")

    companion object {
        val BOTS = listOf("advanced", "elite", "neural", "omega")
        private val NNUE_BOTS = setOf("neural", "omega")

        fun fromOptions(opts: Map<String, String>) = LabelConfig(
            bot = opts["bot"] ?: "advanced",
            depth = opts["depth"]?.toInt() ?: 4,
            nodes = opts["nodes"]?.toInt(),
            net = opts["net"] ?: "default"
        )

        /** A resource name under /nnue (`default`) or a path to a `.nnue` file. */
        fun loadNet(name: String): NnueNetwork =
            if (name.endsWith(".nnue") || '/' in name || '\\' in name) NnueNetwork.load(Paths.get(name))
            else NnueNetwork.fromResource("/nnue/$name.nnue")
    }
}

/**
 * Searches positions with a [LabelConfig] bot and turns the result into a training label. One
 * instance per thread: it holds a bot per colour (the bots' contempt is colour-aware, so each side
 * to move is searched by its own bot), and the bots are not thread-safe.
 */
class PositionLabeler(private val config: LabelConfig) {

    private val network = if (config.bot == "neural" || config.bot == "omega") LabelConfig.loadNet(config.net) else null
    private val white = createBot(Colour.WHITE)
    private val black = createBot(Colour.BLACK)

    /** The searched move, plus its score from White's view when the position is fit to train on. */
    class Label(val move: Move, val whiteScore: Int?)

    fun label(game: ChessGame): Label {
        val sideToMove = game.board.activeColour
        val inCheck = game.isInCheck(sideToMove)
        val bot = if (sideToMove == Colour.WHITE) white else black

        val move = bot.chooseMove(game)
        val best = bot.lastSearchMove
        // Noisy positions are skipped: a static evaluator cannot resolve tactics (quiescence does
        // that at play time), and decided positions have nothing left to teach.
        val quiet = best != null && best.capturedPiece == null && best.promotionType == null
        val score = bot.lastSearchScore
        val usable = quiet && !inCheck && abs(score) <= SCORE_CAP
        return Label(move, if (!usable) null else if (sideToMove == Colour.WHITE) score else -score)
    }

    private fun createBot(colour: Colour): NegamaxBot {
        val depth = if (config.nodes == null) config.depth else null
        val bot = when (config.bot) {
            "advanced" -> AdvancedNegamaxBot(colour, fixedDepth = depth, useOpeningBookMoves = false, transpositionTableBits = 18)
            "elite" -> EliteNegamaxBot(colour, fixedDepth = depth, useOpeningBookMoves = false, transpositionTableBits = 18)
            "neural" -> NeuralNegamaxBot(
                colour, fixedDepth = depth, useOpeningBookMoves = false, transpositionTableBits = 18,
                evaluator = NnueEvaluator(network!!)
            )
            "omega" -> OmegaNegamaxBot(
                colour, fixedDepth = depth, useOpeningBookMoves = false, transpositionTableBits = 18,
                evaluator = NnueEvaluator(network!!)
            )
            else -> error("unreachable: validated by LabelConfig")
        }
        config.nodes?.let { bot.nodeLimit = it }
        return bot
    }

    companion object {
        const val SCORE_CAP = 1_500   // beyond this the game is decided
    }
}
