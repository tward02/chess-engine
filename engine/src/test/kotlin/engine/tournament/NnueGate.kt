package engine.tournament

import com.tward.engine.board.Colour
import com.tward.engine.player.ChessBot
import com.tward.engine.player.bot.OmegaNegamaxBot
import com.tward.engine.player.bot.TempoNegamaxBot
import com.tward.engine.player.evaluator.NnueEvaluator
import com.tward.engine.player.evaluator.nnue.NnueNetwork
import com.tward.engine.tournament.BotSpec
import com.tward.engine.tournament.OpeningSuite
import com.tward.engine.tournament.PentanomialSprt
import com.tward.engine.tournament.playGame
import com.tward.engine.tournament.winner
import utils.LongRunning
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test

/**
 * The promotion gate for NNUE nets: **candidate vs base** on the deployed bot (OmegaNegamaxBot) on
 * a real clock, each opening of the balanced suite played twice with colours swapped, stopped by a
 * pentanomial SPRT as soon as the answer is clear. A candidate is promoted only on `PASSED`.
 *
 *   .\gradlew.bat :engine:longTest --tests "engine.tournament.NnueGate" "-Pnnue.gate.candidate=../nnue-data/candidate.nnue"
 *
 * Nets are resource names under /nnue (`default`, `default-v0`) or `.nnue` file paths (relative to
 * `engine/`). Defaults reproduce the Phase 0 check: candidate `default-v0` vs base `default`.
 *
 * Options (all `-Pnnue.gate.<name>=`):
 * - `candidate`, `base`: the two nets.
 * - `bot`: `omega` (default, what the VM plays) or `tempo`.
 * - `timeMillis` / `increment`: clock per side, default 10000 + 100 ms.
 * - `fixedDepth`: play untimed at this depth instead, which measures eval quality without speed.
 * - `elo0` / `elo1` (default 0 / 10), `alpha` / `beta` (default 0.05): SPRT hypotheses and error rates.
 * - `maxPairs`: give up as inconclusive after this many pairs (default 2000).
 * - `threads`: concurrent pairs (default half the cores, so timed games aren't starved).
 * - `openings`: suite resource (default /openings/balanced.epd).
 */
@LongRunning
class NnueGate {

    private fun prop(name: String): String? = System.getProperty("nnue.gate.$name")?.takeIf { it.isNotBlank() }

    private val candidate = prop("candidate") ?: "default-v0"
    private val base = prop("base") ?: "default"
    private val botType = prop("bot") ?: "omega"
    private val fixedDepth = prop("fixedDepth")?.toInt()
    private val timeMillis = if (fixedDepth != null) 0 else prop("timeMillis")?.toInt() ?: 10_000
    private val increment = if (fixedDepth != null) 0 else prop("increment")?.toInt() ?: 100
    private val maxPairs = prop("maxPairs")?.toInt() ?: 2_000
    private val threads = prop("threads")?.toInt() ?: maxOf(1, Runtime.getRuntime().availableProcessors() / 2)
    private val openingsResource = prop("openings") ?: OpeningSuite.DEFAULT_RESOURCE

    private val sprt = PentanomialSprt(
        elo0 = prop("elo0")?.toDouble() ?: 0.0,
        elo1 = prop("elo1")?.toDouble() ?: 10.0,
        alpha = prop("alpha")?.toDouble() ?: 0.05,
        beta = prop("beta")?.toDouble() ?: 0.05
    )

    private fun loadNet(name: String): NnueNetwork =
        if (name.endsWith(".nnue") || '/' in name || '\\' in name) NnueNetwork.load(Paths.get(name))
        else NnueNetwork.fromResource("/nnue/$name.nnue")

    private fun spec(label: String, network: NnueNetwork) = BotSpec(label) { colour: Colour ->
        val evaluator = NnueEvaluator(network)   // one per bot: the evaluator is not thread-safe
        val bot: ChessBot = when (botType) {
            "omega" -> OmegaNegamaxBot(colour, fixedDepth = fixedDepth, useOpeningBookMoves = false, evaluator = evaluator)
            "tempo" -> TempoNegamaxBot(colour, fixedDepth = fixedDepth, useOpeningBookMoves = false, evaluator = evaluator)
            else -> error("nnue.gate.bot must be omega or tempo, got $botType")
        }
        bot
    }

    // Candidate's points from one game.
    private fun points(candidateWhite: Boolean, result: com.tward.engine.game.GameResult): Double {
        val w = winner(result) ?: return 0.5
        return if ((w == Colour.WHITE) == candidateWhite) 1.0 else 0.0
    }

    @Test
    fun `nnue promotion gate`() {
        val openings = OpeningSuite.load(openingsResource)
        val candidateSpec = spec("candidate", loadNet(candidate))
        val baseSpec = spec("base", loadNet(base))

        val mode = fixedDepth?.let { "fixed depth $it" } ?: "${timeMillis}ms+${increment}ms"
        println(
            "== NNUE GATE: $candidate vs $base | $botType, $mode | SPRT elo0=${sprt.elo0} elo1=${sprt.elo1} " +
                    "alpha=${sprt.alpha} beta=${sprt.beta} | ${openings.size} openings, $threads threads, max $maxPairs pairs =="
        )

        val decided = AtomicBoolean(false)
        val nextPair = AtomicInteger(0)
        var wins = 0
        var losses = 0
        var draws = 0
        val lock = Any()
        val startNanos = System.nanoTime()

        fun play(white: BotSpec, black: BotSpec, fen: String) =
            playGame(white, black, maxPlies = 400, initialTimeMillis = timeMillis, incrementMillis = increment, startFen = fen)

        val pool = Executors.newFixedThreadPool(threads)
        repeat(threads) {
            pool.execute {
                while (!decided.get()) {
                    val index = nextPair.getAndIncrement()
                    if (index >= maxPairs) break
                    val fen = openings[index % openings.size]
                    val first = points(candidateWhite = true, result = play(candidateSpec, baseSpec, fen))
                    val second = points(candidateWhite = false, result = play(baseSpec, candidateSpec, fen))

                    synchronized(lock) {
                        if (decided.get()) return@synchronized
                        for (p in listOf(first, second)) {
                            when (p) {
                                1.0 -> wins++
                                0.0 -> losses++
                                else -> draws++
                            }
                        }
                        sprt.record(first + second)
                        val status = sprt.status()
                        if (sprt.pairs % 10 == 0L || status != PentanomialSprt.Status.CONTINUE) {
                            println("   ${progressLine(wins, losses, draws, startNanos)}")
                        }
                        if (status != PentanomialSprt.Status.CONTINUE) decided.set(true)
                    }
                }
            }
        }
        pool.shutdown()
        pool.awaitTermination(7, TimeUnit.DAYS)

        val verdict = when (sprt.status()) {
            PentanomialSprt.Status.PASSED -> "PASSED: promote $candidate"
            PentanomialSprt.Status.FAILED -> "FAILED: keep $base"
            PentanomialSprt.Status.CONTINUE -> "INCONCLUSIVE after ${sprt.pairs} pairs: no difference large enough to detect"
        }
        println("== RESULT: $verdict ==")
        println("   ${progressLine(wins, losses, draws, startNanos)}")
    }

    private fun progressLine(wins: Int, losses: Int, draws: Int, startNanos: Long): String {
        val est = sprt.eloEstimate()
        val minutes = (System.nanoTime() - startNanos) / 60_000_000_000.0
        return "pairs=${sprt.pairs} games +$wins -$losses =$draws  penta=${sprt.pentanomial}  " +
                "elo=%+.1f [%+.1f, %+.1f]  LLR=%.2f (%.2f, %.2f)  %.1f min".format(
                    est.elo, est.low, est.high, sprt.llr(), sprt.lowerBound, sprt.upperBound, minutes
                )
    }
}
