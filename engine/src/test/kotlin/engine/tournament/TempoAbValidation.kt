package engine.tournament

import com.tward.engine.board.Colour
import com.tward.engine.player.bot.NeuralNegamaxBot
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
 * A/B harness for the phase-4 bot: TempoNegamaxBot (smart time management) vs NeuralNegamaxBot.
 * Search and evaluator are identical, so the score isolates one variable: how the clock is spent.
 * Time management only matters on the clock, so there is no fixed-depth mode here. Run with:
 *
 *   .\gradlew.bat :engine:longTest --tests "engine.tournament.TempoAbValidation"
 *
 * Resize with -Ptempo.ab.games=100 -Ptempo.ab.timeMillis=6000; -Ptempo.ab.increment=2000 plays a
 * Fischer increment per move, the regime lichess games are actually played under.
 */
@LongRunning
class TempoAbValidation {

    private val games = System.getProperty("tempo.ab.games")?.toInt() ?: 100
    private val timeMillis = System.getProperty("tempo.ab.timeMillis")?.toInt() ?: 6_000
    private val incrementMillis = System.getProperty("tempo.ab.increment")?.toInt() ?: 0

    @Test
    fun `tempo vs neural`() {
        val tempo = BotSpec("Tempo") { TempoNegamaxBot(colour = it) }
        val neural = BotSpec("Neural") { NeuralNegamaxBot(colour = it) }

        var wins = 0
        var losses = 0
        var draws = 0
        val byType = sortedMapOf<String, Int>()

        runBlocking {
            val dispatcher = Dispatchers.Default.limitedParallelism(4)
            (0 until games).map { index ->
                async(dispatcher) {
                    val tempoIsWhite = index % 2 == 0
                    val (white, black) = if (tempoIsWhite) tempo to neural else neural to tempo
                    val result = playGame(
                        white, black, maxPlies = 400,
                        initialTimeMillis = timeMillis, incrementMillis = incrementMillis
                    )
                    tempoIsWhite to result
                }
            }.awaitAll().forEach { (tempoIsWhite, result) ->
                val winnerColour = winner(result)
                val label = when {
                    winnerColour == null -> {
                        draws++
                        "$result"
                    }
                    (winnerColour == Colour.WHITE) == tempoIsWhite -> {
                        wins++
                        "$result (Neural lost)"
                    }
                    else -> {
                        losses++
                        "$result (Tempo lost)"
                    }
                }
                byType.merge(label, 1, Int::plus)
            }
        }

        val mode = "${timeMillis}ms clock" +
                (if (incrementMillis > 0) " + ${incrementMillis}ms increment" else "")
        println("== TEMPO VS NEURAL ($mode): Tempo ${wins}W/${losses}L/${draws}D of $games ==")
        byType.forEach { (label, count) -> println("   $label: $count") }
    }
}
