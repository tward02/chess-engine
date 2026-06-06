package com.tward.engine.player.bot

import com.tward.engine.board.Move
import com.tward.engine.game.ChessGame
import com.tward.engine.player.ChessBot
import kotlin.random.Random

class RandomBot : ChessBot {

    override fun chooseMove(game: ChessGame): Move {
        val legalMoves = game.legalMoves()
        if (legalMoves.isEmpty()) {
            throw IllegalStateException("Cannot move, in checkmate")
        }
        return legalMoves.random(Random.Default)
    }

}
