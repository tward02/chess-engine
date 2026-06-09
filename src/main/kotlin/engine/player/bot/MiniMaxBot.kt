package com.tward.engine.player.bot

import com.tward.engine.board.Colour
import com.tward.engine.board.Move
import com.tward.engine.game.ChessGame
import com.tward.engine.game.GameResult
import com.tward.engine.player.ChessBot
import com.tward.engine.player.evaluator.BasicEvaluator
import com.tward.engine.player.evaluator.Evaluator

class MiniMaxBot(private val depth: Int, private val evaluator: Evaluator = BasicEvaluator(), private val colour: Colour) : ChessBot {

    override fun chooseMove(game: ChessGame): Move {

        var bestMove: Move? = null
        var bestScore = Int.MIN_VALUE

        for (move in game.getLegalMoves()) {

            game.makeMove(move)

            val score =
                -negamax(
                    game,
                    depth - 1
                )

            game.undoMove(move)

            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }
        }

        println("Best move score for ${colour}: $bestScore")

        return bestMove ?: game.getLegalMoves().first()
    }

    //TODO Try traditional minimax again

    private fun negamax(
        game: ChessGame,
        depth: Int
    ): Int {

        val result = game.getGameResult()

        if (result != null) {
            return if (result.isDraw()) {
                0
            } else {
                -(100000 - depth)
            }
        }

        if (depth == 0) {
            return evaluator.evaluate(game, depth)
        }

        var best = Int.MIN_VALUE

        for (move in game.getLegalMoves()) {

            game.makeMove(move)

            val score =
                -negamax(
                    game,
                    depth - 1
                )

            game.undoMove(move)

            best = maxOf(best, score)
        }

        return best
    }
}
