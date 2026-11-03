package com.tward.engine.player.evaluator

import com.tward.engine.board.Board
import com.tward.engine.board.BoardListener
import com.tward.engine.board.Colour
import com.tward.engine.board.Piece
import com.tward.engine.game.ChessGame
import com.tward.engine.player.evaluator.nnue.NnueFeatures
import com.tward.engine.player.evaluator.nnue.NnueNetwork
import kotlin.math.roundToInt

/**
 * Evaluates positions with a trained [NnueNetwork] instead of handcrafted terms. Scores follow the
 * shared convention: centipawns from White's perspective.
 *
 * **Incremental accumulators.** The first layer (both perspectives' hidden "accumulators") is kept
 * current as the search makes and unmakes moves: the evaluator registers itself as the board's
 * [BoardListener] and adds or subtracts one weight column per piece change, a few columns a move,
 * instead of summing all ~32 per evaluation. The weights are quantised to integers
 * ([QUANTISATION] steps per unit), so add-then-subtract is exact and the accumulators never drift
 * however long the game. The output layer stays in floating point, so scores are within a centipawn
 * of the float network's.
 *
 * Correctness never depends on the listener: if the board isn't the one this evaluator is attached
 * to, or another evaluator has taken the board's listener slot (two NNUE bots playing each other),
 * the next evaluation re-attaches and rebuilds from scratch.
 *
 * **Not thread-safe**, and one board at a time: give each bot its own instance.
 */
class NnueEvaluator(private val network: NnueNetwork = NnueNetwork.default()) : Evaluator {

    private val hidden = network.hiddenSize
    private val weights = IntArray(network.ftWeights.size) { quantise(network.ftWeights[it]) }
    private val bias = IntArray(hidden) { quantise(network.ftBias[it]) }
    // The output weights pre-divided by the quantisation, so the clipped integer feeds them directly.
    private val outSideToMove = FloatArray(hidden) { network.outWeights[it] / QUANTISATION }
    private val outOpponent = FloatArray(hidden) { network.outWeights[hidden + it] / QUANTISATION }

    private val whiteAcc = IntArray(hidden)
    private val blackAcc = IntArray(hidden)

    private var board: Board? = null
    private var synced = false

    private val listener = object : BoardListener {
        override fun pieceChanged(col: Int, row: Int, old: Piece?, new: Piece?) {
            if (!synced || old == new) return
            if (old != null) update(old, col, row, -1)
            if (new != null) update(new, col, row, 1)
        }

        override fun boardCleared() {
            synced = false
        }
    }

    override fun evaluate(game: ChessGame, depth: Int): Int {
        val current = game.board
        if (!synced || current !== board || current.listener !== listener) attach(current)
        return output(current.activeColour)
    }

    /** The score with the accumulators rebuilt from the whole board, ignoring the incremental state. */
    internal fun evaluateFromScratch(game: ChessGame): Int {
        refresh(game.board)
        return output(game.board.activeColour)
    }

    private fun attach(target: Board) {
        val previous = board
        if (previous !== target && previous?.listener === listener) previous.listener = null
        board = target
        target.listener = listener
        refresh(target)
    }

    private fun refresh(target: Board) {
        bias.copyInto(whiteAcc)
        bias.copyInto(blackAcc)
        target.forEachPiece { col, row, piece -> update(piece, col, row, 1) }
        synced = target === board
    }

    private fun update(piece: Piece, col: Int, row: Int, sign: Int) {
        val whiteIndex = NnueFeatures.index(Colour.WHITE, piece.colour, piece.type, col, row)
        val whiteBase = whiteIndex * hidden
        val blackBase = NnueFeatures.mirror(whiteIndex) * hidden
        val w = weights
        if (sign > 0) {
            for (h in 0 until hidden) {
                whiteAcc[h] += w[whiteBase + h]
                blackAcc[h] += w[blackBase + h]
            }
        } else {
            for (h in 0 until hidden) {
                whiteAcc[h] -= w[whiteBase + h]
                blackAcc[h] -= w[blackBase + h]
            }
        }
    }

    private fun output(sideToMove: Colour): Int {
        val whiteToMove = sideToMove == Colour.WHITE
        val own = if (whiteToMove) whiteAcc else blackAcc
        val opponent = if (whiteToMove) blackAcc else whiteAcc
        var sum = network.outBias
        for (h in 0 until hidden) {
            sum += outSideToMove[h] * clip(own[h]) + outOpponent[h] * clip(opponent[h])
        }
        val sideToMoveScore = (sum * NnueNetwork.OUTPUT_SCALE).roundToInt().coerceIn(-MAX_SCORE, MAX_SCORE)
        return if (whiteToMove) sideToMoveScore else -sideToMoveScore
    }

    private fun clip(x: Int): Float = (if (x < 0) 0 else if (x > ONE) ONE else x).toFloat()

    private companion object {
        /** Integer steps per unit of the first layer; the clipped ReLU's range 0..1 is 0..[ONE]. */
        const val QUANTISATION = 65536f
        const val ONE = 65536

        // Far below the search's mate bound, so no network output can masquerade as a forced mate.
        const val MAX_SCORE = 30_000

        fun quantise(x: Float): Int = (x * QUANTISATION).roundToInt()
    }
}
