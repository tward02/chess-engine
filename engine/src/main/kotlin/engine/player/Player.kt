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

/**
 * A bot that can think on the opponent's time. [ponder] searches a position (the one the bot
 * expects to face next) with no deadline, so its persistent search state — the transposition
 * table — is warm when the real [ChessBot.chooseMove] for that position, or a nearby one, arrives.
 */
interface Ponderable {
    /**
     * Searches [game] until [stopRequested] returns true (polled from the search, so it may be set
     * from another thread) or the search runs out of depth. Returns nothing: the value is the
     * state left behind. [game] is restored before returning.
     */
    fun ponder(game: ChessGame, stopRequested: () -> Boolean)

    /** The reply the bot's last search expects to [move] in [game], or null when it has none. */
    fun expectedReply(game: ChessGame, move: Move): Move?
}

class HumanPlayer(override val name: String) : Player

class BotPlayer(
    val bot: ChessBot, override val name: String
) : Player
