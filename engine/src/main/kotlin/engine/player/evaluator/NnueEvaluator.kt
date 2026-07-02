package com.tward.engine.player.evaluator

import com.tward.engine.board.Colour
import com.tward.engine.game.ChessGame
import com.tward.engine.player.evaluator.nnue.NnueFeatures
import com.tward.engine.player.evaluator.nnue.NnueNetwork
import kotlin.math.roundToInt

/**
 * Evaluates positions with a trained [NnueNetwork] instead of handcrafted terms. Both perspective
 * accumulators are rebuilt from scratch on every call — with at most 32 active features that is a
 * few thousand adds, comparable to the handcrafted evaluators, so it fits the [Evaluator] contract
 * without needing hooks into make/undo. Scores follow the shared convention: centipawns from White's
 * perspective.
 *
 * **Not thread-safe** (it reuses its accumulator buffers) — give each bot its own instance.
 */
class NnueEvaluator(private val network: NnueNetwork = NnueNetwork.default()) : Evaluator {

    private val whiteAcc = FloatArray(network.hiddenSize)
    private val blackAcc = FloatArray(network.hiddenSize)

    override fun evaluate(game: ChessGame, depth: Int): Int {
        val board = game.board
        val hidden = network.hiddenSize
        val weights = network.ftWeights

        network.ftBias.copyInto(whiteAcc)
        network.ftBias.copyInto(blackAcc)

        for ((square, piece) in board.getPiecesWithSquares()) {
            val whiteBase = NnueFeatures.index(Colour.WHITE, piece, square) * hidden
            val blackBase = NnueFeatures.index(Colour.BLACK, piece, square) * hidden
            for (h in 0 until hidden) {
                whiteAcc[h] += weights[whiteBase + h]
                blackAcc[h] += weights[blackBase + h]
            }
        }

        val whiteToMove = board.activeColour == Colour.WHITE
        val sideToMove = if (whiteToMove) whiteAcc else blackAcc
        val opponent = if (whiteToMove) blackAcc else whiteAcc

        var sum = network.outBias
        for (h in 0 until hidden) sum += network.outWeights[h] * clip(sideToMove[h])
        for (h in 0 until hidden) sum += network.outWeights[hidden + h] * clip(opponent[h])

        val sideToMoveScore = (sum * NnueNetwork.OUTPUT_SCALE).roundToInt().coerceIn(-MAX_SCORE, MAX_SCORE)
        return if (whiteToMove) sideToMoveScore else -sideToMoveScore
    }

    private fun clip(x: Float): Float = if (x < 0f) 0f else if (x > 1f) 1f else x

    private companion object {
        // Far below the search's mate bound, so no network output can masquerade as a forced mate.
        const val MAX_SCORE = 30_000
    }
}