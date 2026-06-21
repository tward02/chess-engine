package com.tward.app

import androidx.compose.material.MaterialTheme
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.game.ChessGame
import com.tward.engine.player.BotPlayer
import com.tward.engine.player.HumanPlayer
import com.tward.engine.player.Player
import com.tward.engine.player.bot.MiniMaxBot
import com.tward.engine.player.bot.MiniMaxIterativeDeepeningBot
import com.tward.engine.player.evaluator.AdaptiveEvaluator
import com.tward.engine.player.ordering.KillerHistoryMoveOrderer
import com.tward.logging.Log
import com.tward.logging.LogConfig
import com.tward.ui.model.ChessMatch
import com.tward.ui.model.ClockManager
import com.tward.ui.model.TimeControl
import com.tward.ui.view.game.BoardView

/**
 * Single-game application. Configure [whitePlayer] and [blackPlayer] below as any mix of
 * [HumanPlayer] and [BotPlayer]. Humans move by clicking or dragging; bots move automatically.
 */
fun main() = application {

    LogConfig.configure()  // raise to Level.FINE for per-move and bot-search detail

    val log = Log.of("com.tward.app.GameApp")
    log.info { "Starting head-to-head game" }

    // --- Configure the match here ---
    val whitePlayer: Player = BotPlayer(
        MiniMaxBot(
            depth = 5,
            colour = Colour.WHITE,
            evaluator = AdaptiveEvaluator(),
            moveOrderer = KillerHistoryMoveOrderer()
        ), name = "MiniMax"
    )
    val blackPlayer: Player = BotPlayer(
        MiniMaxIterativeDeepeningBot(
            colour = Colour.BLACK,
            evaluator = AdaptiveEvaluator(),
            moveOrderer = KillerHistoryMoveOrderer()
        ), name = "MiniMax Iterative Deepening"
    )
    // Alternatives: BotPlayer(...) + BotPlayer(...) for bot-vs-bot, HumanPlayer() + HumanPlayer() for 2-player
    val timeControl = TimeControl(initialMillis = 120_000, incrementMillis = 200)
    // --------------------------------

    val match = ChessMatch(
        ChessGame(Board.getStartingBoard()),
        whitePlayer,
        blackPlayer,
        ClockManager(timeControl)
    )

    val windowState = rememberWindowState(
        size = DpSize(820.dp, 900.dp)
    )

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Chess"
    ) {

        MaterialTheme {

            BoardView(match, showEvaluationBar = true)
        }
    }
}
