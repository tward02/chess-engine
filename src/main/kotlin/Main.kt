package com.tward

import androidx.compose.material.MaterialTheme
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tward.engine.board.Board
import com.tward.engine.game.ChessGame
import com.tward.engine.game.ChessMatch
import com.tward.engine.game.ClockManager
import com.tward.engine.game.TimeControl
import com.tward.engine.player.BotPlayer
import com.tward.engine.player.HumanPlayer
import com.tward.engine.player.bot.RandomBot
import com.tward.ui.BoardView

fun main() = application {

    val windowState = rememberWindowState(
        size = DpSize(800.dp, 800.dp)
    )

    val board = Board()

    board.setupStandardPosition()

//    val match =
//        ChessMatch(ChessGame(board), HumanPlayer(), BotPlayer(RandomBot()), ClockManager(TimeControl(60000, 200)))

    val match =
        ChessMatch(ChessGame(board), BotPlayer(RandomBot()), BotPlayer(RandomBot()), ClockManager(TimeControl(60000, 200)))

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Chess"
    ) {

        MaterialTheme {

            BoardView(match)
        }
    }
}
