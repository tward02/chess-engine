package com.tward.engine.tournament

import com.tward.engine.game.GameResult

/**
 * A single scheduled game within a multi-bot tournament round.
 *
 * Most pairings are an ordinary [white] vs [black] game. A pairing with [bye] set represents a bot
 * sitting out the round (odd field in Swiss / knockout); no game is played and the bye auto-scores a
 * win for that contender. A bye pairing has no [white]/[black].
 */
data class Pairing(
    val round: Int,
    val board: Int,
    val white: BotSpec? = null,
    val black: BotSpec? = null,
    val bye: BotSpec? = null
) {
    val isBye: Boolean get() = bye != null

    companion object {
        fun game(round: Int, board: Int, white: BotSpec, black: BotSpec) =
            Pairing(round, board, white = white, black = black)

        fun bye(round: Int, board: Int, contender: BotSpec) =
            Pairing(round, board, bye = contender)
    }
}

/** A completed [Pairing] and the [GameResult] it produced. Byes carry a synthetic win result. */
data class GameOutcome(val pairing: Pairing, val result: GameResult)
