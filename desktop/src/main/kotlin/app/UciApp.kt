package com.tward.app

import com.tward.engine.bench.Benchmark
import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.game.ChessGame
import com.tward.engine.player.bot.OmegaNegamaxBot
import com.tward.engine.player.evaluator.NnueEvaluator
import com.tward.engine.player.evaluator.nnue.NnueNetwork
import com.tward.logging.LogConfig
import com.tward.uci.UciEngine
import java.util.concurrent.atomic.AtomicBoolean

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
 *
 * `java -jar uci-engine.jar bench [depth]` runs the fixed-depth speed benchmark on the deployed bot
 * instead (nodes/s on this machine; the node count fingerprints the search + net).
 */
// The strongest net in the NNUE round robin (see engine/src/main/resources/nnue/README.md).
private const val NETWORK = "/nnue/default.nnue"

fun main(args: Array<String>) {
    // Logs to stderr only; UCI protocol traffic stays on stdout uncorrupted.
    LogConfig.configure()
    if (args.firstOrNull() == "bench") {
        bench(args.getOrNull(1)?.toIntOrNull() ?: Benchmark.DEFAULT_DEPTH)
        return
    }

    // lichess-bot starts a fresh JVM per game, so the first searches would otherwise run in the
    // interpreter while the JIT catches up. Warm the search up until the first real `go`.
    val warmupStop = AtomicBoolean(false)
    startWarmup(warmupStop)

    val engine = UciEngine(
        output = { line ->
            println(line)
            System.out.flush()
        },
        // One bot per process here, so it can afford a far larger transposition table (4M entries)
        // than the in-server default.
        botFactory = { colour -> deployedBot(colour) },
        asyncSearch = true
    )

    generateSequence(::readLine).forEach { line ->
        if (line.trimStart().startsWith("go")) warmupStop.set(true)
        if (!engine.handle(line)) {
            return
        }
    }
    engine.awaitSearch()
}

private const val WARMUP_MILLIS = 3_000L

// A typical middlegame, so the hot paths (move generation, NNUE, pruning) all get compiled.
private const val WARMUP_FEN = "r1bq1rk1/pp2bppp/2n1pn2/2pp4/3P4/2PBPN2/PP1N1PPP/R1BQ1RK1 w - - 0 8"

private fun startWarmup(stop: AtomicBoolean) {
    Thread({
        val deadline = System.nanoTime() + WARMUP_MILLIS * 1_000_000
        // Same classes and network as the real bot (JIT profiles are per method, not per instance),
        // but a small table so it costs no real memory.
        val bot = OmegaNegamaxBot(
            colour = Colour.WHITE,
            evaluator = NnueEvaluator(NnueNetwork.fromResource(NETWORK)),
            transpositionTableBits = 16
        )
        bot.ponder(ChessGame(Board.fromFEN(WARMUP_FEN))) { stop.get() || System.nanoTime() > deadline }
    }, "uci-warmup").apply {
        isDaemon = true
        priority = Thread.MIN_PRIORITY
        start()
    }
}

private fun deployedBot(colour: Colour, fixedDepth: Int? = null) = OmegaNegamaxBot(
    colour = colour,
    fixedDepth = fixedDepth,
    useOpeningBookMoves = fixedDepth == null,
    evaluator = NnueEvaluator(NnueNetwork.fromResource(NETWORK)),
    transpositionTableBits = 22
)

private fun bench(depth: Int) {
    println("Warming up the JIT...")
    Benchmark.run(depth = minOf(depth, 7), bot = { deployedBot(it, minOf(depth, 7)) })
    println("Benchmark: $NETWORK, depth $depth over ${Benchmark.POSITIONS.size} positions")
    val result = Benchmark.run(depth = depth, bot = { deployedBot(it, depth) }) { println("  $it") }
    println("Total: $result")
}
