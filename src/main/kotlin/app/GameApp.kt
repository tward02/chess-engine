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
import com.tward.engine.player.evaluator.StandardEvaluator
import com.tward.engine.player.ordering.KillerHistoryMoveOrderer
import com.tward.logging.Log
import com.tward.logging.LogConfig
import com.tward.ui.model.ChessMatch
import com.tward.ui.model.ClockManager
import com.tward.ui.model.TimeControl
import com.tward.ui.views.BoardView
import java.util.logging.Level

/**
 * Head-to-head application: a single game shown on the board.
 *
 * Configure [whitePlayer] and [blackPlayer] below as any mix of [HumanPlayer] and
 * [BotPlayer] — human vs bot, bot vs bot, or human vs human. A human moves by clicking
 * or dragging pieces; bots move on their own.
 */
fun main() = application {

    // Raise to Level.FINE to also see per-move, book-move and bot-search detail
    LogConfig.configure(Level.WARNING)

    val log = Log.of("com.tward.app.GameApp")
    log.info { "Starting head-to-head game" }

    // --- Configure the match here ---
    val whitePlayer: Player = BotPlayer(
        MiniMaxBot(depth = 5, colour = Colour.WHITE, evaluator = StandardEvaluator(), moveOrderer = KillerHistoryMoveOrderer()), name = "MiniMax 1")
    val blackPlayer: Player = BotPlayer(
        MiniMaxBot(depth = 5, colour = Colour.BLACK, evaluator = StandardEvaluator(), moveOrderer = KillerHistoryMoveOrderer()), name = "MiniMax 2")
    // Other setups:
    //   Bot vs bot     -> BotPlayer(MiniMaxBot(... WHITE ...)) and BotPlayer(MiniMaxBot(... BLACK ...))
    //   Human vs human -> HumanPlayer() and HumanPlayer()
    val timeControl = TimeControl(initialMillis = 300_000, incrementMillis = 200)
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
