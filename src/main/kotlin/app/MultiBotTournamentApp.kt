package com.tward.app

import androidx.compose.material.MaterialTheme
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tward.engine.player.bot.AdvancedNegamaxBot
import com.tward.engine.player.bot.MiniMaxBot
import com.tward.engine.player.bot.MiniMaxIterativeDeepeningBot
import com.tward.engine.player.bot.NegamaxBot
import com.tward.engine.player.evaluator.*
import com.tward.engine.player.ordering.KillerHistoryMoveOrderer
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
        BotSpec("Positional ADNM KHO") { colour ->
            AdvancedNegamaxBot(
                colour = colour,
                evaluator = PositionalEvaluator(),
                moveOrderer = KillerHistoryMoveOrderer()
            )
        },
        BotSpec("Adaptive ADMN KHO") { colour ->
            AdvancedNegamaxBot(
                colour = colour,
                evaluator = AdaptiveEvaluator(),
                moveOrderer = KillerHistoryMoveOrderer()
            )
        },
        BotSpec("Standard ADNM KHO") { colour ->
            AdvancedNegamaxBot(
                colour = colour,
                evaluator = StandardEvaluator(),
                moveOrderer = KillerHistoryMoveOrderer()
            )
        },
        BotSpec("Advanced ADMN KHO") { colour ->
            AdvancedNegamaxBot(
                colour = colour,
                evaluator = AdaptiveEvaluator(),
                moveOrderer = KillerHistoryMoveOrderer()
            )
        },
        BotSpec("Compact ADMN KHO") { colour ->
            AdvancedNegamaxBot(
                colour = colour,
                evaluator = CompactEvaluator(),
                moveOrderer = KillerHistoryMoveOrderer()
            )
        },

        BotSpec("Positional Negamax KHO") { colour ->
            NegamaxBot(
                colour = colour,
                evaluator = PositionalEvaluator(),
                moveOrderer = KillerHistoryMoveOrderer()
            )
        },
        BotSpec("Adaptive Negamax KHO") { colour ->
            NegamaxBot(
                colour = colour,
                evaluator = AdaptiveEvaluator(),
                moveOrderer = KillerHistoryMoveOrderer()
            )
        },
        BotSpec("Standard Negamax KHO") { colour ->
            NegamaxBot(
                colour = colour,
                evaluator = StandardEvaluator(),
                moveOrderer = KillerHistoryMoveOrderer()
            )
        },
        BotSpec("Advanced Negamax KHO") { colour ->
            NegamaxBot(
                colour = colour,
                evaluator = AdaptiveEvaluator(),
                moveOrderer = KillerHistoryMoveOrderer()
            )
        },
        BotSpec("Compact Negamax KHO") { colour ->
            NegamaxBot(
                colour = colour,
                evaluator = CompactEvaluator(),
                moveOrderer = KillerHistoryMoveOrderer()
            )
        },

        BotSpec("Positional ID KHO") { colour ->
            MiniMaxIterativeDeepeningBot(
                colour = colour,
                evaluator = PositionalEvaluator(),
                moveOrderer = KillerHistoryMoveOrderer()
            )
        },
        BotSpec("Adaptive ID KHO") { colour ->
            MiniMaxIterativeDeepeningBot(
                colour = colour,
                evaluator = AdaptiveEvaluator(),
                moveOrderer = KillerHistoryMoveOrderer()
            )
        },
        BotSpec("Standard ID KHO") { colour ->
            MiniMaxIterativeDeepeningBot(
                colour = colour,
                evaluator = StandardEvaluator(),
                moveOrderer = KillerHistoryMoveOrderer()
            )
        },
        BotSpec("Advanced ID KHO") { colour ->
            MiniMaxIterativeDeepeningBot(
                colour = colour,
                evaluator = AdaptiveEvaluator(),
                moveOrderer = KillerHistoryMoveOrderer()
            )
        },
        BotSpec("Compact ID KHO") { colour ->
            MiniMaxIterativeDeepeningBot(
                colour = colour,
                evaluator = CompactEvaluator(),
                moveOrderer = KillerHistoryMoveOrderer()
            )
        },

        BotSpec("Positional MM KHO") { colour ->
            MiniMaxBot(
                depth = 4,
                colour = colour,
                evaluator = PositionalEvaluator(),
                moveOrderer = KillerHistoryMoveOrderer()
            )
        },
        BotSpec("Adaptive MM KHO") { colour ->
            MiniMaxBot(
                depth = 4,
                colour = colour,
                evaluator = AdaptiveEvaluator(),
                moveOrderer = KillerHistoryMoveOrderer()
            )
        },
        BotSpec("Standard MM KHO") { colour ->
            MiniMaxBot(
                depth = 4,
                colour = colour,
                evaluator = StandardEvaluator(),
                moveOrderer = KillerHistoryMoveOrderer()
            )
        },
        BotSpec("Compact MM KHO") { colour ->
            MiniMaxBot(
                depth = 4,
                colour = colour,
                evaluator = CompactEvaluator(),
                moveOrderer = KillerHistoryMoveOrderer()
            )
        },
        BotSpec("Advanced MM KHO") { colour ->
            MiniMaxBot(
                depth = 4,
                colour = colour,
                evaluator = AdvancedEvaluator(),
                moveOrderer = KillerHistoryMoveOrderer()
            )
        },



    )

    // Swap in KnockoutFormat() or SwissFormat(rounds = 5) to try other formats
    val tournament = MultiBotTournament(
        contenders = contenders,
        format = SwissFormat(20),
        initialTimeMillis = 180_000,
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
