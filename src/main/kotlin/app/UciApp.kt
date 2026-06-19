package com.tward.app

import com.tward.engine.player.bot.MiniMaxIterativeDeepeningBot
import com.tward.engine.player.evaluator.PositionalEvaluator
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
        botFactory = { colour -> MiniMaxIterativeDeepeningBot(colour = colour, evaluator = PositionalEvaluator()) }
    )

    generateSequence(::readLine).forEach { line ->
        if (!engine.handle(line)) {
            return
        }
    }
}