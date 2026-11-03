package engine.tournament

import com.tward.engine.board.Colour
import com.tward.engine.player.bot.OmegaNegamaxBot
import com.tward.engine.player.bot.TempoNegamaxBot
import com.tward.engine.tournament.BotSpec
import com.tward.engine.tournament.playGame
import com.tward.engine.tournament.winner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import utils.LongRunning
import kotlin.test.Test

/**
 * A/B harness for the phase-5 bot: OmegaNegamaxBot (singular extensions, improving heuristic, IIR,
 * quiescence TT, SEE move pruning, two-fold repetition scoring) vs TempoNegamaxBot. Evaluator,
 * orderer and time policy are identical, so the score isolates the per-node search changes. Run:
 *
 *   .\gradlew.bat :engine:longTest --tests "engine.tournament.OmegaAbValidation"
 *
 * Resize with -Pomega.ab.games=100 -Pomega.ab.timeMillis=6000; -Pomega.ab.increment=2000 plays a
 * Fischer increment per move; -Pomega.ab.fixedDepth=6 plays untimed at equal depth instead
 * (isolates tree quality with speed removed).
 */
@LongRunning
class OmegaAbValidation {

    private val games = System.getProperty("omega.ab.games")?.toInt() ?: 100
    private val timeMillis = System.getProperty("omega.ab.timeMillis")?.toInt() ?: 6_000
    private val incrementMillis = System.getProperty("omega.ab.increment")?.toInt() ?: 0
    private val fixedDepth = System.getProperty("omega.ab.fixedDepth")?.toInt()

    @Test
    fun `omega vs tempo`() {
        val omega = BotSpec("Omega") { OmegaNegamaxBot(colour = it, fixedDepth = fixedDepth) }
        val tempo = BotSpec("Tempo") { TempoNegamaxBot(colour = it, fixedDepth = fixedDepth) }

        var wins = 0
        var losses = 0
        var draws = 0
        val byType = sortedMapOf<String, Int>()

        runBlocking {
            val dispatcher = Dispatchers.Default.limitedParallelism(4)
            (0 until games).map { index ->
                async(dispatcher) {
                    val omegaIsWhite = index % 2 == 0
                    val (white, black) = if (omegaIsWhite) omega to tempo else tempo to omega
                    val result = playGame(
                        white, black, maxPlies = 400,
                        initialTimeMillis = if (fixedDepth != null) 0 else timeMillis,
                        incrementMillis = incrementMillis
                    )
                    omegaIsWhite to result
                }
            }.awaitAll().forEach { (omegaIsWhite, result) ->
                val winnerColour = winner(result)
                val label = when {
                    winnerColour == null -> {
                        draws++
                        "$result"
                    }
                    (winnerColour == Colour.WHITE) == omegaIsWhite -> {
                        wins++
                        "$result (Tempo lost)"
                    }
                    else -> {
                        losses++
                        "$result (Omega lost)"
                    }
                }
                byType.merge(label, 1, Int::plus)
            }
        }

        val mode = if (fixedDepth != null) "fixed depth $fixedDepth" else {
            "${timeMillis}ms clock" + (if (incrementMillis > 0) " + ${incrementMillis}ms increment" else "")
        }
        println("== OMEGA VS TEMPO ($mode): Omega ${wins}W/${losses}L/${draws}D of $games ==")
        byType.forEach { (label, count) -> println("   $label: $count") }
    }
}
