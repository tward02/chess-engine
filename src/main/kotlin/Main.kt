package com.tward

import androidx.compose.material.MaterialTheme
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.game.ChessGame
import com.tward.ui.ChessMatch
import com.tward.ui.ClockManager
import com.tward.ui.TimeControl
import com.tward.engine.player.BotPlayer
import com.tward.engine.player.HumanPlayer
import com.tward.engine.player.bot.MiniMaxBot
import com.tward.engine.player.bot.RandomBot
import com.tward.engine.player.evaluator.BasicEvaluator
import com.tward.engine.player.evaluator.StandardEvaluator
import com.tward.ui.BoardView

fun main() = application {

    val windowState = rememberWindowState(
        size = DpSize(800.dp, 800.dp)
    )

    val board = Board()

    board.setupStandardPosition()

//    val board = Board.fromFEN("8/P7/8/8/4k3/8/8/4K3 w - - 0 1")

    val match =
        ChessMatch(
            ChessGame(board),
            BotPlayer(MiniMaxBot(depth = 3, colour = Colour.WHITE, evaluator = StandardEvaluator())),
            BotPlayer(MiniMaxBot(depth = 3, colour = Colour.BLACK, evaluator = StandardEvaluator())),
            ClockManager(TimeControl(300000, 200))
        )

//    val match =
//        ChessMatch(ChessGame(board), BotPlayer(RandomBot()), BotPlayer(RandomBot()), ClockManager(TimeControl(60000, 200)))

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
