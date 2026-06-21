package com.tward.engine.tournament

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.game.ChessGame
import com.tward.engine.game.GameResult

/**
 * Plays a single game between two bot specs to a finished [GameResult]. Fresh bots are built per
 * game because bots hold per-game state (opening-book progress).
 *
 * Time handling mirrors the 2-bot [Tournament]: when [initialTimeMillis] > 0 each side is charged
 * its actual thinking time and running to zero is an immediate loss; 0 means untimed (bots get 0
 * and never flag). A game that never resolves within [maxPlies] is adjudicated a draw.
 */
internal fun playGame(
    white: BotSpec,
    black: BotSpec,
    maxPlies: Int,
    initialTimeMillis: Int
): GameResult {

    val whiteBot = white.createBot(Colour.WHITE)
    val blackBot = black.createBot(Colour.BLACK)

    val game = ChessGame(Board.getStartingBoard())

    val timed = initialTimeMillis > 0
    var whiteTime = initialTimeMillis
    var blackTime = initialTimeMillis

    var plies = 0
    while (plies < maxPlies) {
        val result = game.getGameResult()
        if (result != null) return result

        val whiteToMove = game.board.activeColour == Colour.WHITE
        val bot = if (whiteToMove) whiteBot else blackBot
        val timeLeft = if (whiteToMove) whiteTime else blackTime

        val startNanos = System.nanoTime()
        val move = bot.chooseMove(game, timeLeft)

        if (timed) {
            val elapsedMs = ((System.nanoTime() - startNanos) / 1_000_000).toInt()
            if (whiteToMove) {
                whiteTime -= elapsedMs
                if (whiteTime <= 0) return GameResult.BLACK_TIME_WIN
            } else {
                blackTime -= elapsedMs
                if (blackTime <= 0) return GameResult.WHITE_TIME_WIN
            }
        }

        game.makeMove(move)
        plies++
    }

    return GameResult.DRAW_50_MOVE_RULE
}

/** The winning colour of a finished game, or null for a draw. Centralizes win detection. */
internal fun winner(result: GameResult): Colour? = when (result) {
    GameResult.WHITE_WIN, GameResult.WHITE_TIME_WIN, GameResult.BLACK_RESIGNATION -> Colour.WHITE
    GameResult.BLACK_WIN, GameResult.BLACK_TIME_WIN, GameResult.WHITE_RESIGNATION -> Colour.BLACK
    else -> null
}
