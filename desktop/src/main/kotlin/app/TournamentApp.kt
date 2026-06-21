package com.tward.app

import androidx.compose.material.MaterialTheme
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tward.engine.player.bot.AdvancedNegamaxBot
import com.tward.engine.player.bot.NegamaxBot
import com.tward.engine.tournament.BotSpec
import com.tward.engine.tournament.Tournament
import com.tward.logging.Log
import com.tward.logging.LogConfig
import com.tward.ui.view.tournament.TournamentView

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
    val specA = BotSpec("Advanced Negamax") { colour ->
        AdvancedNegamaxBot(
            colour = colour
        )
    }

    val specB = BotSpec("Negamax") { colour ->
        NegamaxBot(
            colour = colour,
        )
    }

    val tournament = Tournament(specA, specB, totalGames = 24, initialTimeMillis = 180_000)

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
