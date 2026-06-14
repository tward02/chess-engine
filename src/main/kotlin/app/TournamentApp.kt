package com.tward.app

import androidx.compose.material.MaterialTheme
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tward.engine.player.bot.MiniMaxBot
import com.tward.engine.player.evaluator.AdaptiveEvaluator
import com.tward.engine.player.evaluator.StandardEvaluator
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
 * Configure the two contenders ([specA], [specB]) and [Tournament.totalGames] below.
 */
fun main() = application {

    // Raise to Level.FINE to also see per-move, book-move and bot-search detail
    LogConfig.configure()

    val log = Log.of("com.tward.app.TournamentApp")
    log.info { "Starting bot tournament" }

    val windowState = rememberWindowState(
        size = DpSize(1040.dp, 840.dp)
    )

    // Each spec builds a fresh bot per game (bots hold per-game state, so they can't be shared).
    // The opening book gives the deterministic minimax search varied openings across games.
    val specA = BotSpec("MiniMax Phased Evaluator") { colour ->
        MiniMaxBot(depth = 4, colour = colour, evaluator = AdaptiveEvaluator(), moveOrderer = KillerHistoryMoveOrderer())
    }

    val specB = BotSpec("MiniMax") { colour ->
        MiniMaxBot(depth = 4, colour = colour, evaluator = StandardEvaluator(), moveOrderer = KillerHistoryMoveOrderer())
    }

    val tournament = Tournament(specA, specB, totalGames = 1000)

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
