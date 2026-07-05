package engine.tournament

import com.tward.engine.board.Colour
import com.tward.engine.player.bot.ApexNegamaxBot
import com.tward.engine.player.bot.NeuralNegamaxBot
import com.tward.engine.player.evaluator.NnueEvaluator
import com.tward.engine.player.evaluator.nnue.NnueNetwork
import com.tward.engine.tournament.BotSpec
import com.tward.engine.tournament.playGame
import com.tward.engine.tournament.winner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import utils.LongRunning
import java.nio.file.Paths
import kotlin.test.Test

/**
 * A/B harness for the phase-3 bot: NeuralNegamaxBot (learned NNUE evaluation) vs ApexNegamaxBot
 * (handcrafted evaluation). The two share the identical search, so the score isolates one variable:
 * the evaluator. Run with:
 *
 *   .\gradlew.bat :engine:longTest --tests "engine.tournament.NeuralAbValidation"
 *
 * Resize from the command line with -Pnnue.ab.games=100 -Pnnue.ab.timeMillis=6000. Passing
 * -Pnnue.ab.fixedDepth=6 instead plays untimed at exactly that depth for both bots — that removes
 * evaluation speed from the comparison, so the score measures evaluation quality alone. Passing
 * -Pnnue.ab.network=build/nnue/candidate.nnue makes Neural play with that net file instead of the
 * bundled one, so candidate nets can be A/B'd without overwriting the resource.
 */
@LongRunning
class NeuralAbValidation {

    private val games = System.getProperty("nnue.ab.games")?.toInt() ?: 100
    private val timeMillis = System.getProperty("nnue.ab.timeMillis")?.toInt() ?: 6_000
    private val fixedDepth = System.getProperty("nnue.ab.fixedDepth")?.toInt()
    private val networkPath = System.getProperty("nnue.ab.network")

    @Test
    fun `neural vs apex`() {
        val network = networkPath?.let { NnueNetwork.load(Paths.get(it)) }
        val neural = BotSpec("Neural") {
            NeuralNegamaxBot(
                colour = it, maxThinkTimeMillis = 4_000, fixedDepth = fixedDepth,
                evaluator = network?.let(::NnueEvaluator) ?: NnueEvaluator()
            )
        }
        val apex = BotSpec("Neural 2") {
            ApexNegamaxBot(colour = it, maxThinkTimeMillis = 4_000, fixedDepth = fixedDepth)
        }
        val initialTime = if (fixedDepth != null) 0 else timeMillis   // fixed depth plays untimed

        var wins = 0
        var losses = 0
        var draws = 0
        val byType = sortedMapOf<String, Int>()

        runBlocking {
            val dispatcher = Dispatchers.Default.limitedParallelism(4)
            (0 until games).map { index ->
                async(dispatcher) {
                    val neuralIsWhite = index % 2 == 0
                    val (white, black) = if (neuralIsWhite) neural to apex else apex to neural
                    val result = playGame(white, black, maxPlies = 400, initialTimeMillis = initialTime)
                    neuralIsWhite to result
                }
            }.awaitAll().forEach { (neuralIsWhite, result) ->
                val winnerColour = winner(result)
                val label = when {
                    winnerColour == null -> {
                        draws++
                        "$result"
                    }
                    (winnerColour == Colour.WHITE) == neuralIsWhite -> {
                        wins++
                        "$result (Apex lost)"
                    }
                    else -> {
                        losses++
                        "$result (Neural lost)"
                    }
                }
                byType.merge(label, 1, Int::plus)
            }
        }

        val mode = (fixedDepth?.let { "fixed depth $it" } ?: "${timeMillis}ms clock") +
                (networkPath?.let { ", net $it" } ?: "")
        println("== NEURAL VS APEX ($mode): Neural ${wins}W/${losses}L/${draws}D of $games ==")
        byType.forEach { (label, count) -> println("   $label: $count") }
    }
}
