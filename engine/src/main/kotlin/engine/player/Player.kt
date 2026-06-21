package com.tward.engine.player

import com.tward.engine.board.Move
import com.tward.engine.game.ChessGame

sealed interface Player {
    val name: String
}

interface ChessBot {
    fun chooseMove(game: ChessGame, timeLeft: Int = 0): Move
}

class HumanPlayer(override val name: String) : Player

class BotPlayer(
    val bot: ChessBot, override val name: String
) : Player
