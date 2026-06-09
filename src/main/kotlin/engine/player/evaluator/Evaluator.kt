package com.tward.engine.player.evaluator

import com.tward.engine.game.ChessGame

interface Evaluator {

    fun evaluate(game: ChessGame, depth: Int = 0): Int

}
