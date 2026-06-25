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
        val maximising = game.board.activeColour == Colour.WHITE
        val inCheck = game.isInCheck(game.board.activeColour)
        val standPat = base.evaluate(game)

        if (pliesLeft == 0) return standPat

        // When in check the side to move is forced to respond, so there is no "stand pat" floor and
        // every legal evasion must be explored — quiet blocks and king moves included, not just
        // captures. This is what removes the swing from a check that is trivially answered: the
        // position is played past the check before [base] scores it, instead of [base] seeing a
        // raw in-check board and over/under-valuing it.
        val moves = if (inCheck) {
            game.getLegalMoves()
        } else {
            game.getLegalMoves().filter { it.capturedPiece != null || it.promotionType != null }
        }
        if (moves.isEmpty()) return standPat

        val ordered = captureOrderer.order(moves, ply = 0)

        if (maximising) {
            // No stand-pat floor in check: must move, so start below every score.
            var best = if (inCheck) Int.MIN_VALUE else standPat
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
            var best = if (inCheck) Int.MAX_VALUE else standPat
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
