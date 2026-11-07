package com.tward.app

import androidx.compose.material.MaterialTheme
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tward.engine.player.bot.OmegaNegamaxBot
import com.tward.engine.player.evaluator.NnueEvaluator
import com.tward.engine.player.evaluator.nnue.NnueNetwork.Companion.fromResource
import com.tward.engine.tournament.BotSpec
import com.tward.engine.tournament.MultiBotTournament
import com.tward.engine.tournament.format.SwissFormat
import com.tward.logging.Log
import com.tward.logging.LogConfig
import com.tward.ui.view.tournament.MultiBotTournamentView

/**
 * Multi-bot tournament application: runs three or more bots against each other in a chess tournament
 * format (round-robin, knockout, or Swiss), showing a live standings table and one randomly chosen
 * in-progress game. The 2-bot A/B [TournamentApp] is unchanged and still available for head-to-head
 * bot testing.
 *
 * To run this entry point, set the Compose main class in build.gradle:
 *   compose.desktop { application { mainClass = 'com.tward.app.MultiBotTournamentAppKt' } }
 *
 * Configure the contenders and format below.
 */
fun main() = application {

    LogConfig.configure()  // raise to Level.FINE for per-move and bot-search detail

    val log = Log.of("com.tward.app.MultiBotTournamentApp")
    log.info { "Starting multi-bot tournament" }

    val windowState = rememberWindowState(
        size = DpSize(1140.dp, 840.dp)
    )

    // Each spec builds a fresh bot per game; the opening book gives varied openings across games
    val contenders = listOf(
        BotSpec("Default v0 Negamax") { colour ->
            OmegaNegamaxBot(
                colour = colour,
                evaluator = NnueEvaluator( fromResource("/nnue/default-v0.nnue"))
            )
        },
                BotSpec("Default Negamax") { colour ->
            OmegaNegamaxBot(
                colour = colour,
                evaluator = NnueEvaluator( fromResource("/nnue/default.nnue"))
            )
        },

        BotSpec("Default v1 Negamax") { colour ->
            OmegaNegamaxBot(
                colour = colour,
                evaluator = NnueEvaluator( fromResource("/nnue/default-v1.nnue"))
            )
        },
        BotSpec("Default v2 Negamax") { colour ->
            OmegaNegamaxBot(
                colour = colour,
                evaluator = NnueEvaluator( fromResource("/nnue/default-v2.nnue"))
            )
        },
        BotSpec("Default v3 Negamax") { colour ->
            OmegaNegamaxBot(
                colour = colour,
                evaluator = NnueEvaluator( fromResource("/nnue/default-v0.nnue"))
            )
        },

        )

    // Swap in KnockoutFormat() or SwissFormat(rounds = 5) to try other formats
    val tournament = MultiBotTournament(
        contenders = contenders,
        format = SwissFormat(20),
        initialTimeMillis = 60_000,
        reserveOneForDisplay = true
    )

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Multi-Bot Tournament"
    ) {

        MaterialTheme {

            MultiBotTournamentView(tournament)
        }
    }
}
