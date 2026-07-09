package com.tward.engine.player.bot

import com.tward.engine.board.Colour
import com.tward.engine.board.Move
import com.tward.engine.game.ChessGame
import com.tward.engine.game.GameResult
import com.tward.engine.openingBook.OpeningBook
import com.tward.engine.player.ChessBot
import com.tward.engine.player.evaluator.Evaluator
import com.tward.engine.player.evaluator.StandardEvaluator
import com.tward.engine.player.ordering.KillerHistoryMoveOrderer
import com.tward.engine.player.ordering.MoveOrderer
import com.tward.logging.Log

const val CHECKMATE_SCORE = 100000

open class MiniMaxBot(
    private val depth: Int,
    private val colour: Colour,
    private val aggression: Int = 10,
    private val useOpeningBookMoves: Boolean = true,
    private val evaluator: Evaluator = StandardEvaluator(aggression = aggression),
    // A fresh stateful orderer per bot (default args are evaluated per construction, and the bot
    // factories build a new bot per game, so the killer/history tables are never shared).
    private val moveOrderer: MoveOrderer = KillerHistoryMoveOrderer()
) : ChessBot {

    private val log = Log.of(this)   // runtime class, so subclasses log under their own bot name

    val openingBook = OpeningBook("/moveBook/Book.txt")
    var numberOfOpeningMoves = 0

    // Positions searched for the current move; the bot is single-threaded per game so a plain field
    // is safe. Exposed (read-only) so the effect of move ordering can be measured.
    var nodesSearched = 0
        protected set

    override fun chooseMove(game: ChessGame, timeLeft: Int): Move {

        val legalMoves = game.getLegalMoves()

        if (numberOfOpeningMoves < 5 && useOpeningBookMoves) {
            val bookMove = getBookMove(game, legalMoves)
            if (bookMove != null) {
                return bookMove
            }
        }

        val maximising = game.board.activeColour == Colour.WHITE

        var bestMove: Move? = null
        var bestScore = if (maximising) Int.MIN_VALUE else Int.MAX_VALUE
        var alpha = Int.MIN_VALUE
        var beta = Int.MAX_VALUE

        nodesSearched = 0
        moveOrderer.reset()
        val startNanos = System.nanoTime()

        // Root is ply 0; search most-promising moves first so alpha/beta tightens early
        for (move in moveOrderer.order(legalMoves, ply = 0)) {

            game.makeMove(move)
            val score = minimax(game, depth - 1, alpha, beta, !maximising, ply = 1)
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

    protected open fun getBookMove(game: ChessGame, legalMoves: List<Move>): Move? {
        numberOfOpeningMoves++
        val fen = game.board.toFEN(isFullFEN = false)

        openingBook.getBookMove(fen)?.moveStr?.let { bookMoveStr ->
            legalMoves.firstOrNull { it.toAlgebraic() == bookMoveStr }?.let {
                log.debug { "$colour played book move ${it.toAlgebraic()} (move ${numberOfOpeningMoves})" }
                return it
            }
        }

        return null
    }

    protected open fun minimax(
        game: ChessGame,
        depth: Int,
        alpha: Int,
        beta: Int,
        maximising: Boolean,
        ply: Int
    ): Int {

        nodesSearched++

        val result = game.getGameResult()

        if (result != null) {
            // Earlier mates score higher (depth bonus rewards finding them sooner)
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

        val orderedMoves = moveOrderer.order(game.getLegalMoves(), ply)

        if (maximising) {
            var best = Int.MIN_VALUE

            for (move in orderedMoves) {
                game.makeMove(move)
                val score = minimax(game, depth - 1, currentAlpha, currentBeta, false, ply + 1)
                game.undoMove(move)

                best = maxOf(best, score)
                currentAlpha = maxOf(currentAlpha, best)

                if (currentBeta <= currentAlpha) {
                    moveOrderer.onBetaCutoff(move, ply, depth)
                    break
                }
            }

            return best
        } else {
            var best = Int.MAX_VALUE

            for (move in orderedMoves) {
                game.makeMove(move)
                val score = minimax(game, depth - 1, currentAlpha, currentBeta, true, ply + 1)
                game.undoMove(move)

                best = minOf(best, score)
                currentBeta = minOf(currentBeta, best)

                if (currentBeta <= currentAlpha) {
                    moveOrderer.onBetaCutoff(move, ply, depth)
                    break
                }
            }

            return best
        }
    }
}
