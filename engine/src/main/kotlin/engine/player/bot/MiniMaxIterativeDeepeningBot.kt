package com.tward.engine.player.bot

import com.tward.engine.board.Colour
import com.tward.engine.board.Move
import com.tward.engine.game.ChessGame
import com.tward.engine.player.evaluator.Evaluator
import com.tward.engine.player.evaluator.StandardEvaluator
import com.tward.engine.player.ordering.KillerHistoryMoveOrderer
import com.tward.engine.player.ordering.MoveOrderer
import com.tward.logging.Log
import kotlin.math.ceil
import kotlin.math.max

class MiniMaxIterativeDeepeningBot(
    private val maxDepth: Int = 10,
    private val colour: Colour,
    private val aggression: Int = 10,
    private val useOpeningBookMoves: Boolean = true,
    private val useMaxTime: Boolean = true,
    evaluator: Evaluator = StandardEvaluator(aggression = aggression),
    private val moveOrderer: MoveOrderer = KillerHistoryMoveOrderer()
) : MiniMaxBot(maxDepth, colour, aggression, useOpeningBookMoves, evaluator, moveOrderer) {

    private val log = Log.of(this)

    private val maxThinkTime = 20000
    private val increment = 200

    private var deadline = 0L          // nanosecond deadline set before the ID loop
    private var searchAborted = false  // set by minimax on timeout; causes root to discard partial result

    override fun chooseMove(game: ChessGame, timeLeft: Int): Move {

        val legalMoves = game.getLegalMoves()

        if (numberOfOpeningMoves < 5 && useOpeningBookMoves) {
            val bookMove = getBookMove(game, legalMoves)
            if (bookMove != null) {
                return bookMove
            }
        }

        val thinkTime = chooseThinkTime(timeLeft)
        deadline = System.nanoTime() + thinkTime * 1_000_000L

        val maximising = game.board.activeColour == Colour.WHITE
        var bestMove = legalMoves.first()
        var previousBestMove: Move? = null

        nodesSearched = 0
        searchAborted = false
        moveOrderer.reset()
        val startNanos = System.nanoTime()

        for (depth in 1..maxDepth) {
            val (move, score) = searchRoot(game, depth, maximising, previousBestMove)
            if (searchAborted) {
                break
            }

            bestMove = move
            previousBestMove = move

            log.debug {
                val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
                "$colour depth=$depth score=$score nodes=$nodesSearched in ${elapsedMs}ms"
            }

            if (score >= CHECKMATE_SCORE || score <= -CHECKMATE_SCORE) {
                break
            }
        }

        log.info {
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
            "$colour chose ${bestMove.toAlgebraic()} nodes=$nodesSearched total=${elapsedMs}ms think time=${thinkTime}ms"
        }

        return bestMove
    }

    // Root search for one depth. Separated from minimax so bestMove can be tracked here, and so
    // previousBestMove can be promoted to the front of the list for faster alpha/beta tightening.
    private fun searchRoot(
        game: ChessGame,
        depth: Int,
        maximising: Boolean,
        previousBestMove: Move?
    ): Pair<Move, Int> {
        val moves = if (previousBestMove != null) {
            listOf(previousBestMove) + (moveOrderer.order(game.getLegalMoves(), ply = 0) - previousBestMove)
        } else {
            moveOrderer.order(game.getLegalMoves(), ply = 0)
        }
        var bestMove = moves.first()
        var bestScore = if (maximising) Int.MIN_VALUE else Int.MAX_VALUE
        var alpha = Int.MIN_VALUE
        var beta = Int.MAX_VALUE

        for (move in moves) {
            game.makeMove(move)
            val score = minimax(game, depth - 1, alpha, beta, !maximising, ply = 1)
            game.undoMove(move)

            if (searchAborted) {
                return Pair(bestMove, bestScore)
            }

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

        return Pair(bestMove, bestScore)
    }

    override fun minimax(
        game: ChessGame,
        depth: Int,
        alpha: Int,
        beta: Int,
        maximising: Boolean,
        ply: Int
    ): Int {
        if ((nodesSearched % TIME_CHECK_INTERVAL) == 0 && System.nanoTime() >= deadline) {
            searchAborted = true
            return 0
        }

        return super.minimax(game, depth, alpha, beta, maximising, ply)
    }

    private fun chooseThinkTime(timeLeft: Int): Int {
        // ~1/40th of remaining time (assumes ~40 moves left to play)
        var thinkTime = timeLeft / 40.0

        // Increment is recovered after the move, so it's safe to spend now; cap after adding it
        thinkTime += increment

        if (useMaxTime) {
            thinkTime = minOf(thinkTime, maxThinkTime.toDouble())
        }

        // Never spend more than 90% of remaining time to avoid flagging
        thinkTime = minOf(thinkTime, timeLeft * 0.9)

        val minThinkTime = minOf(50.toDouble(), timeLeft * 0.25)
        return ceil(max(minThinkTime, thinkTime)).toInt()
    }

    companion object {
        private const val TIME_CHECK_INTERVAL = 2048
    }
}
