package engine.tournament

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.Move
import com.tward.engine.game.ChessGame
import com.tward.engine.game.GameResult
import com.tward.engine.tournament.OpeningSuite
import com.tward.engine.tournament.PentanomialSprt
import com.tward.engine.tournament.winner
import com.tward.uci.UciMoveCodec
import utils.LongRunning
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test

/**
 * **Engine-vs-engine gate over UCI**: two `uci-engine.jar` builds, each in its own JVM exactly as
 * lichess-bot runs it, play the balanced opening suite (each opening twice, colours swapped) on a
 * real clock, stopped by the same pentanomial SPRT as [NnueGate]. Use it for changes the in-process
 * gates can't compare: search or inference code (two versions of the same classes can't share a
 * JVM), or the deployed jar against a candidate.
 *
 *   .\gradlew.bat :engine:longTest --tests "engine.tournament.UciMatch" "-Puci.match.candidate=C:\path\new.jar" "-Puci.match.base=C:\path\old.jar"
 *
 * Options (all `-Puci.match.<name>=`): `candidate`, `base` (jar paths, required); `timeMillis` /
 * `increment` (default 10000 + 100 ms); `elo0` / `elo1` (default 0 / 10); `alpha` / `beta` (0.05);
 * `maxPairs` (2000); `threads` (concurrent games, default half the cores); `openings`.
 */
@LongRunning
class UciMatch {

    private fun prop(name: String): String? = System.getProperty("uci.match.$name")?.takeIf { it.isNotBlank() }

    private val timeMillis = prop("timeMillis")?.toInt() ?: 10_000
    private val increment = prop("increment")?.toInt() ?: 100
    private val maxPairs = prop("maxPairs")?.toInt() ?: 2_000
    private val threads = prop("threads")?.toInt() ?: maxOf(1, Runtime.getRuntime().availableProcessors() / 2)
    private val openingsResource = prop("openings") ?: OpeningSuite.DEFAULT_RESOURCE

    private val sprt = PentanomialSprt(
        elo0 = prop("elo0")?.toDouble() ?: 0.0,
        elo1 = prop("elo1")?.toDouble() ?: 10.0,
        alpha = prop("alpha")?.toDouble() ?: 0.05,
        beta = prop("beta")?.toDouble() ?: 0.05
    )

    private fun jar(name: String): Path {
        val path = Paths.get(prop(name) ?: error("-Puci.match.$name=<path to uci-engine.jar> is required"))
        require(Files.isRegularFile(path)) { "uci.match.$name: no such jar: $path" }
        return path.toAbsolutePath()
    }

    @Test
    fun `uci engine match`() {
        val candidate = jar("candidate")
        val base = jar("base")
        val openings = OpeningSuite.load(openingsResource)
        println(
            "== UCI MATCH: ${candidate.fileName} vs ${base.fileName} | ${timeMillis}ms+${increment}ms | " +
                    "SPRT elo0=${sprt.elo0} elo1=${sprt.elo1} | ${openings.size} openings, $threads threads =="
        )
        println("   candidate: $candidate")
        println("   base:      $base")

        val decided = AtomicBoolean(false)
        val nextPair = AtomicInteger(0)
        var wins = 0
        var losses = 0
        var draws = 0
        val endings = HashMap<String, Int>()
        val lock = Any()
        val startNanos = System.nanoTime()

        val pool = Executors.newFixedThreadPool(threads)
        repeat(threads) {
            pool.execute {
                val engines = mutableMapOf<Path, EngineProcess>()
                fun engine(jar: Path) = engines.getOrPut(jar) { EngineProcess(jar) }
                try {
                    while (!decided.get()) {
                        val index = nextPair.getAndIncrement()
                        if (index >= maxPairs) break
                        val fen = openings[index % openings.size]
                        val first = play(engine(candidate), engine(base), fen, candidateWhite = true, engines)
                        val second = play(engine(base), engine(candidate), fen, candidateWhite = false, engines)

                        synchronized(lock) {
                            if (decided.get()) return@synchronized
                            for (game in listOf(first, second)) {
                                when (game.candidatePoints) {
                                    1.0 -> wins++
                                    0.0 -> losses++
                                    else -> draws++
                                }
                                endings.merge(game.ending, 1, Int::plus)
                            }
                            sprt.record(first.candidatePoints + second.candidatePoints)
                            val status = sprt.status()
                            if (sprt.pairs % 10 == 0L || status != PentanomialSprt.Status.CONTINUE) {
                                println("   ${progressLine(wins, losses, draws, startNanos)}")
                            }
                            if (status != PentanomialSprt.Status.CONTINUE) decided.set(true)
                        }
                    }
                } finally {
                    engines.values.forEach(EngineProcess::close)
                }
            }
        }
        pool.shutdown()
        pool.awaitTermination(7, TimeUnit.DAYS)

        val verdict = when (sprt.status()) {
            PentanomialSprt.Status.PASSED -> "PASSED: the candidate is stronger"
            PentanomialSprt.Status.FAILED -> "FAILED: no gain of elo1 or more"
            PentanomialSprt.Status.CONTINUE -> "INCONCLUSIVE after ${sprt.pairs} pairs"
        }
        println("== RESULT: $verdict ==")
        println("   ${progressLine(wins, losses, draws, startNanos)}")
        println("   endings: ${endings.entries.sortedByDescending { it.value }.joinToString { "${it.key}=${it.value}" }}")
    }

    private class GameOutcome(val candidatePoints: Double, val ending: String)

    /** One game; an engine that crashes, hangs or plays an illegal move loses it and is restarted. */
    private fun play(
        white: EngineProcess,
        black: EngineProcess,
        fen: String,
        candidateWhite: Boolean,
        engines: MutableMap<Path, EngineProcess>
    ): GameOutcome {
        fun outcome(result: GameResult, ending: String = result.name): GameOutcome {
            val w = winner(result) ?: return GameOutcome(0.5, ending)
            return GameOutcome(if ((w == Colour.WHITE) == candidateWhite) 1.0 else 0.0, ending)
        }
        fun forfeit(loser: Colour, why: String, engine: EngineProcess): GameOutcome {
            engine.close()
            engines.remove(engine.jar)   // a fresh process next game
            return outcome(if (loser == Colour.WHITE) GameResult.BLACK_WIN else GameResult.WHITE_WIN, why)
        }

        white.newGame()
        black.newGame()
        val game = ChessGame(Board.fromFEN(fen))
        val moves = ArrayList<String>()
        var whiteTime = timeMillis.toLong()
        var blackTime = timeMillis.toLong()

        for (ply in 0 until MAX_PLIES) {
            game.getGameResult()?.let { return outcome(it) }
            val side = game.board.activeColour
            val engine = if (side == Colour.WHITE) white else black
            val clock = if (side == Colour.WHITE) whiteTime else blackTime

            val start = System.nanoTime()
            val reply = engine.bestMove(fen, moves, whiteTime, blackTime, increment, timeoutMillis = clock + HANG_GRACE_MILLIS)
                ?: return forfeit(side, "${side}_NO_REPLY", engine)
            val elapsed = (System.nanoTime() - start) / 1_000_000
            if (clock - elapsed <= 0) return outcome(if (side == Colour.WHITE) GameResult.BLACK_TIME_WIN else GameResult.WHITE_TIME_WIN)
            if (side == Colour.WHITE) whiteTime += increment - elapsed else blackTime += increment - elapsed

            val move: Move = game.getLegalMoves().firstOrNull { UciMoveCodec.encode(it) == reply }
                ?: return forfeit(side, "${side}_ILLEGAL", engine)
            game.makeMove(move)
            moves += reply
        }
        return GameOutcome(0.5, "MAX_PLIES")
    }

    private fun progressLine(wins: Int, losses: Int, draws: Int, startNanos: Long): String {
        val est = sprt.eloEstimate()
        val minutes = (System.nanoTime() - startNanos) / 60_000_000_000.0
        return "pairs=${sprt.pairs} games +$wins -$losses =$draws  penta=${sprt.pentanomial}  " +
                "elo=%+.1f [%+.1f, %+.1f]  LLR=%.2f (%.2f, %.2f)  %.1f min".format(
                    est.elo, est.low, est.high, sprt.llr(), sprt.lowerBound, sprt.upperBound, minutes
                )
    }

    /** A UCI engine in its own JVM, run with the same Java as this test. Lines are read on a thread. */
    private class EngineProcess(val jar: Path) {
        private val process: Process = ProcessBuilder(javaExecutable(), "-jar", jar.toString())
            .redirectError(ProcessBuilder.Redirect.DISCARD)   // engine logs go to stderr
            .start()
        private val input = PrintWriter(process.outputStream, true)
        private val lines = LinkedBlockingQueue<String>()

        init {
            Thread({
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    while (true) lines.put(reader.readLine() ?: break)
                }
            }, "uci-reader-${jar.fileName}").apply { isDaemon = true }.start()
            send("uci")
            check(await("uciok", STARTUP_TIMEOUT_MILLIS) != null) { "$jar did not answer uci" }
        }

        fun newGame() {
            send("ucinewgame")
            send("isready")
            check(await("readyok", STARTUP_TIMEOUT_MILLIS) != null) { "$jar did not answer isready" }
        }

        /** The engine's move in UCI notation, or null if it died or exceeded [timeoutMillis]. */
        fun bestMove(fen: String, moves: List<String>, wtime: Long, btime: Long, inc: Int, timeoutMillis: Long): String? {
            send("position fen $fen" + if (moves.isEmpty()) "" else " moves ${moves.joinToString(" ")}")
            send("go wtime ${wtime.coerceAtLeast(1)} btime ${btime.coerceAtLeast(1)} winc $inc binc $inc")
            return await("bestmove", timeoutMillis)?.split(' ')?.getOrNull(1)
        }

        private fun send(command: String) {
            input.println(command)
        }

        private fun await(prefix: String, timeoutMillis: Long): String? {
            val deadline = System.nanoTime() + timeoutMillis * 1_000_000
            while (true) {
                val remaining = (deadline - System.nanoTime()) / 1_000_000
                if (remaining <= 0 || !process.isAlive && lines.isEmpty()) return null
                val line = lines.poll(minOf(remaining, 200), TimeUnit.MILLISECONDS) ?: continue
                if (line.startsWith(prefix)) return line
            }
        }

        fun close() {
            runCatching { send("quit") }
            if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
        }

        private companion object {
            fun javaExecutable(): String = ProcessHandle.current().info().command().orElse("java")
        }
    }

    private companion object {
        const val MAX_PLIES = 400
        const val HANG_GRACE_MILLIS = 5_000L
        const val STARTUP_TIMEOUT_MILLIS = 30_000L
    }
}
