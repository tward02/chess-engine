package com.tward.engine.player.evaluator

import com.tward.engine.board.Colour
import com.tward.engine.game.ChessGame
import com.tward.engine.player.ordering.MvvLvaMoveOrderer

/**
 * Wraps any [Evaluator] with a quiescence search: before scoring a position it plays out the
 * pending forcing moves (captures and promotions) until the board is "quiet", then hands the
 * resulting position to [base].
 *
 * This removes the large score swings a purely static evaluator shows mid-exchange — e.g. a queen
 * trade reading as +9 the instant one queen is taken, snapping back once it's recaptured.
 *
 * Scores keep [base]'s convention: centipawns from White's perspective. Safe to reuse [game]
 * afterwards (moves are undone). Stateless across calls, so as thread-safe as [base] is.
 */
class QuiescenceEvaluator(
    private val base: Evaluator,
    private val maxPlies: Int = 8
) : Evaluator {

    private val captureOrderer = MvvLvaMoveOrderer()

    override fun evaluate(game: ChessGame, depth: Int): Int {
        return quiesce(game, Int.MIN_VALUE, Int.MAX_VALUE, maxPlies)
    }

    private fun quiesce(game: ChessGame, alpha: Int, beta: Int, pliesLeft: Int): Int {
        // Stand pat: side to move isn't obliged to capture, so the static score bounds this node.
        val standPat = base.evaluate(game)
        if (pliesLeft == 0) return standPat

        val forcingMoves = game.getLegalMoves()
            .filter { it.capturedPiece != null || it.promotionType != null }
        if (forcingMoves.isEmpty()) return standPat

        val ordered = captureOrderer.order(forcingMoves, ply = 0)
        val maximising = game.board.activeColour == Colour.WHITE

        if (maximising) {
            var best = standPat
            var currentAlpha = maxOf(alpha, best)
            for (move in ordered) {
                game.makeMove(move)
                val score = quiesce(game, currentAlpha, beta, pliesLeft - 1)
                game.undoMove(move)

                if (score > best) best = score
                if (best > currentAlpha) currentAlpha = best
                if (currentAlpha >= beta) break
            }
            return best
        } else {
            var best = standPat
            var currentBeta = minOf(beta, best)
            for (move in ordered) {
                game.makeMove(move)
                val score = quiesce(game, alpha, currentBeta, pliesLeft - 1)
                game.undoMove(move)

                if (score < best) best = score
                if (best < currentBeta) currentBeta = best
                if (alpha >= currentBeta) break
            }
            return best
        }
    }
}
