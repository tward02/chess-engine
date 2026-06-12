package com.tward.engine.player.bot

import com.tward.engine.board.Colour
import com.tward.engine.board.Move
import com.tward.engine.game.ChessGame
import com.tward.engine.game.GameResult
import com.tward.engine.player.ChessBot
import com.tward.engine.player.evaluator.BasicEvaluator
import com.tward.engine.player.evaluator.Evaluator

private const val CHECKMATE_SCORE = 100000

class MiniMaxBot(private val depth: Int, private val evaluator: Evaluator = BasicEvaluator(), private val colour: Colour) : ChessBot {

    override fun chooseMove(game: ChessGame): Move {

        val maximising = game.board.activeColour == Colour.WHITE

        var bestMove: Move? = null
        var bestScore = if (maximising) Int.MIN_VALUE else Int.MAX_VALUE
        var alpha = Int.MIN_VALUE
        var beta = Int.MAX_VALUE

        for (move in game.getLegalMoves()) {

            game.makeMove(move)
            val score = minimax(game, depth - 1, alpha, beta, !maximising)
            game.undoMove(move)

            if (maximising) {
                if (score > bestScore) {
                    bestScore = score
                    bestMove = move
                }
                alpha = maxOf(alpha, bestScore)
            } else {
                if (score < bestScore) {
                    bestScore = score
                    bestMove = move
                }
                beta = minOf(beta, bestScore)
            }
        }

        return bestMove ?: game.getLegalMoves().first()
    }

    private fun minimax(
        game: ChessGame,
        depth: Int,
        alpha: Int,
        beta: Int,
        maximising: Boolean
    ): Int {

        val result = game.getGameResult()

        if (result != null) {
            // Add the remaining depth so mates found earlier in the search score better
            return when {
                result.isDraw() -> 0
                result == GameResult.WHITE_WIN -> CHECKMATE_SCORE + depth
                else -> -(CHECKMATE_SCORE + depth)
            }
        }

        if (depth == 0) {
            return evaluator.evaluate(game, depth)
        }

        var currentAlpha = alpha
        var currentBeta = beta

        if (maximising) {
            var best = Int.MIN_VALUE

            for (move in game.getLegalMoves()) {
                game.makeMove(move)
                val score = minimax(game, depth - 1, currentAlpha, currentBeta, false)
                game.undoMove(move)

                best = maxOf(best, score)
                currentAlpha = maxOf(currentAlpha, best)

                if (currentBeta <= currentAlpha) {
                    break
                }
            }

            return best
        } else {
            var best = Int.MAX_VALUE

            for (move in game.getLegalMoves()) {
                game.makeMove(move)
                val score = minimax(game, depth - 1, currentAlpha, currentBeta, true)
                game.undoMove(move)

                best = minOf(best, score)
                currentBeta = minOf(currentBeta, best)

                if (currentBeta <= currentAlpha) {
                    break
                }
            }

            return best
        }
    }
}
