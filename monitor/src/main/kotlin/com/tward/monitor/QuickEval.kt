package com.tward.monitor

import com.tward.engine.board.Board
import com.tward.engine.game.ChessGame
import com.tward.engine.player.evaluator.PositionalEvaluator
import com.tward.engine.player.evaluator.QuiescenceEvaluator

/**
 * A cheap eval of a live position for the dashboard's eval bar: the same quiescence + positional
 * evaluator the desktop UI's bar uses. It is not the bot's search score. Not thread-safe (the
 * evaluator isn't); the poller calls it from one coroutine.
 */
class QuickEval {

    private val evaluator = QuiescenceEvaluator(PositionalEvaluator())
    private val cache = object : LinkedHashMap<String, Int?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int?>) = size > 64
    }

    /** Centipawns from White's perspective, or null if the FEN can't be read. */
    fun whiteCp(fen: String): Int? = cache.getOrPut(fen) {
        runCatching { evaluator.evaluate(ChessGame(Board.fromFEN(fen)), 0) }.getOrNull()
    }

    fun forBot(game: LiveGame): LiveGame {
        val cp = whiteCp(game.fen) ?: return game
        return game.copy(evalCp = if (game.colour == "white") cp else -cp)
    }
}
