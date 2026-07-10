package com.tward.engine.player

import com.tward.engine.board.Move
import com.tward.engine.game.ChessGame

sealed interface Player {
    val name: String
}

interface ChessBot {
    fun chooseMove(game: ChessGame, timeLeft: Int = 0): Move
}

/**
 * A bot whose time management uses the per-move clock increment (Fischer bonus). Drivers that know
 * the increment (UCI `go winc`/`binc`, the tournament game loop) set [incrementMillis] before the
 * game's moves are requested; bots that don't implement this budget from the main clock alone.
 */
interface ClockAware {
    var incrementMillis: Int
}

class HumanPlayer(override val name: String) : Player

class BotPlayer(
    val bot: ChessBot, override val name: String
) : Player
