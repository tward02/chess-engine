package com.tward.engine.player.bot

import com.tward.engine.board.Colour
import com.tward.engine.board.Move
import com.tward.engine.game.ChessGame
import com.tward.engine.game.GameResult
import com.tward.engine.openingBook.OpeningBook
import com.tward.engine.player.ChessBot
import com.tward.engine.player.evaluator.BasicEvaluator
import com.tward.engine.player.evaluator.Evaluator
import com.tward.logging.Log

private const val CHECKMATE_SCORE = 100000

class MiniMaxBot(private val depth: Int, private val evaluator: Evaluator = BasicEvaluator(), private val colour: Colour, val useOpeningBookMoves: Boolean = true) : ChessBot {

    private val log = Log.of<MiniMaxBot>()

    val openingBook = OpeningBook("/moveBook/Book.txt")
    var numberOfOpeningMoves = 0

    // Positions searched for the current move; the bot is single-threaded per game so a plain field is safe
    private var nodesSearched = 0

    override fun chooseMove(game: ChessGame): Move {

        val legalMoves = game.getLegalMoves()

        if (numberOfOpeningMoves < 5 && useOpeningBookMoves) {
            numberOfOpeningMoves++
            val fen = game.board.toFEN(isFullFEN = false)

            openingBook.getBookMove(fen)?.moveStr?.let { bookMoveStr ->
                legalMoves.firstOrNull { it.toAlgebraic() == bookMoveStr }?.let {
                    log.debug { "$colour played book move ${it.toAlgebraic()} (move ${numberOfOpeningMoves})" }
                    return it
                }
            }
        }

        val maximising = game.board.activeColour == Colour.WHITE

        var bestMove: Move? = null
        var bestScore = if (maximising) Int.MIN_VALUE else Int.MAX_VALUE
        var alpha = Int.MIN_VALUE
        var beta = Int.MAX_VALUE

        nodesSearched = 0
        val startNanos = System.nanoTime()

        for (move in legalMoves) {

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

        if (bestMove == null) {
            log.warn { "$colour found no best move at depth $depth; falling back to first legal move" }
        }

        val chosen = bestMove ?: legalMoves.first()

        log.debug {
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
            "$colour chose ${chosen.toAlgebraic()} score=$bestScore depth=$depth nodes=$nodesSearched in ${elapsedMs}ms"
        }

        return chosen
    }

    private fun minimax(
        game: ChessGame,
        depth: Int,
        alpha: Int,
        beta: Int,
        maximising: Boolean
    ): Int {

        nodesSearched++

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
