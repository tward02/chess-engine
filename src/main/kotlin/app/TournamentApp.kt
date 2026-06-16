package com.tward.app

import androidx.compose.material.MaterialTheme
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tward.engine.player.bot.MiniMaxBot
import com.tward.engine.player.bot.MiniMaxIterativeDeepeningBot
import com.tward.engine.player.evaluator.AdaptiveEvaluator
import com.tward.engine.player.evaluator.PositionalEvaluator
import com.tward.engine.player.ordering.KillerHistoryMoveOrderer
import com.tward.engine.tournament.BotSpec
import com.tward.engine.tournament.Tournament
import com.tward.logging.Log
import com.tward.logging.LogConfig
import com.tward.ui.views.TournamentView

/**
 * Tournament application: plays two bots against each other many times to measure their
 * relative strength. One game is shown live while the rest run headless across a thread
 * pool; results are tallied and displayed alongside the board.
 *
 * Configure contenders and game count below.
 */
fun main() = application {

    LogConfig.configure()  // raise to Level.FINE for per-move and bot-search detail

    val log = Log.of("com.tward.app.TournamentApp")
    log.info { "Starting bot tournament" }

    val windowState = rememberWindowState(
        size = DpSize(1040.dp, 840.dp)
    )

    // Each spec builds a fresh bot per game; the opening book gives varied openings across games
    val specA = BotSpec("Position Evaluator") { colour ->
        MiniMaxIterativeDeepeningBot(
            colour = colour,
            evaluator = PositionalEvaluator(),
            moveOrderer = KillerHistoryMoveOrderer()
        )
    }

    val specB = BotSpec("MiniMaxDepth 4") { colour ->
        MiniMaxBot(
            depth = 4,
            colour = colour,
            evaluator = AdaptiveEvaluator(),
            moveOrderer = KillerHistoryMoveOrderer()
        )
    }

    val tournament = Tournament(specA, specB, totalGames = 100, initialTimeMillis = 180_000)

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Bot Tournament"
    ) {

        MaterialTheme {

            TournamentView(tournament)
        }
    }
}
