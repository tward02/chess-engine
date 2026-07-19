package com.tward.app

import com.tward.engine.player.bot.TempoNegamaxBot
import com.tward.engine.player.evaluator.NnueEvaluator
import com.tward.engine.player.evaluator.nnue.NnueNetwork
import com.tward.logging.LogConfig
import com.tward.uci.UciEngine

/**
 * UCI entry point: speaks the Universal Chess Interface over stdin/stdout so the
 * engine can be driven by a UCI GUI (Arena, Cute Chess, BanksiaGUI) or, via
 * `lichess-bot`, by Lichess itself to earn a rating.
 *
 * Run this main class instead of the Compose apps:
 *   1. Set `compose.desktop { application { mainClass = 'com.tward.app.UciAppKt' } }`
 *      in build.gradle (or build a jar — see UCI_LICHESS.md).
 *   2. `.\gradlew.bat run`
 *
 * Logging goes to stderr (java.util.logging's ConsoleHandler), keeping stdout a
 * clean UCI channel. Configure the bot the engine plays as below.
 */
fun main() {
    // Logs to stderr only; UCI protocol traffic stays on stdout uncorrupted.
    LogConfig.configure()

    val engine = UciEngine(
        output = { line ->
            println(line)
            System.out.flush()
        },
        // One bot per process here, so it can afford a far larger transposition table (4M entries)
        // than the in-server default.
        botFactory = { colour ->
            TempoNegamaxBot(
                colour = colour,
                evaluator = NnueEvaluator(NnueNetwork.fromResource("/nnue/default-better.nnue")),
                transpositionTableBits = 22
            )
        }
    )

    generateSequence(::readLine).forEach { line ->
        if (!engine.handle(line)) {
            return
        }
    }
}