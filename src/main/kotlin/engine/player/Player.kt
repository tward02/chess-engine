package com.tward.engine.player

import com.tward.engine.board.Move
import com.tward.engine.game.ChessGame

sealed interface Player

interface ChessBot {
    fun chooseMove(game: ChessGame): Move
}

class HumanPlayer : Player

class BotPlayer(
    val bot: ChessBot
) : Player

