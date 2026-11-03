package engine.tournament

import com.tward.engine.board.Colour
import com.tward.engine.player.ChessBot
import com.tward.engine.player.bot.OmegaNegamaxBot
import com.tward.engine.player.bot.TempoNegamaxBot
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
import kotlin.math.ln
import kotlin.test.Test

/**
 * Round robin between the bundled NNUE nets, each driving the same bot (TempoNegamaxBot, or Omega
 * with -Pnnue.cmp.bot=omega), so
 * the evaluator is the only variable. The nets share one architecture, so they evaluate equally
 * fast and the default fixed-depth mode measures evaluation quality alone. Run with:
 *
 *   .\gradlew.bat :engine:longTest --tests "engine.tournament.NnueNetComparison"
 *
 * Resize with -Pnnue.cmp.games=<per pairing> and -Pnnue.cmp.fixedDepth=<depth>, or play on a clock
 * with -Pnnue.cmp.timeMillis=<ms> (overrides fixed depth). Pick nets with
 * -Pnnue.cmp.nets=default,default-v0 (resource names under /nnue).
 */
@LongRunning
class NnueNetComparison {

    private val nets = (System.getProperty("nnue.cmp.nets") ?: "default,default-v0,iter1,iter2").split(",")
    private val gamesPerPairing = System.getProperty("nnue.cmp.games")?.toInt() ?: 12
    private val timeMillis = System.getProperty("nnue.cmp.timeMillis")?.toInt()
    private val fixedDepth = if (timeMillis != null) null else System.getProperty("nnue.cmp.fixedDepth")?.toInt() ?: 5

    private val botType = System.getProperty("nnue.cmp.bot") ?: "tempo"

    private fun spec(net: String) = BotSpec(net) { colour ->
        val evaluator = NnueEvaluator(NnueNetwork.fromResource("/nnue/$net.nnue"))
        val bot: ChessBot = when (botType) {
            "omega" -> OmegaNegamaxBot(colour, fixedDepth = fixedDepth, transpositionTableBits = 18, evaluator = evaluator)
            else -> TempoNegamaxBot(colour, fixedDepth = fixedDepth, transpositionTableBits = 18, evaluator = evaluator)
        }
        bot
    }

    @Test
    fun `nnue round robin`() {
        val specs = nets.associateWith(::spec)
        val pairings = nets.indices.flatMap { i -> (i + 1 until nets.size).map { j -> nets[i] to nets[j] } }
        val points = nets.associateWith { 0.0 }.toMutableMap()
        val head = mutableMapOf<Pair<String, String>, DoubleArray>()   // (a,b) -> [aW, aL, D]

        runBlocking {
            val dispatcher = Dispatchers.Default.limitedParallelism(4)
            pairings.flatMap { (a, b) ->
                (0 until gamesPerPairing).map { index ->
                    async(dispatcher) {
                        val aIsWhite = index % 2 == 0
                        val (white, black) = if (aIsWhite) specs.getValue(a) to specs.getValue(b)
                        else specs.getValue(b) to specs.getValue(a)
                        val result = playGame(white, black, maxPlies = 300, initialTimeMillis = timeMillis ?: 0)
                        Triple(a to b, aIsWhite, winner(result))
                    }
                }
            }.awaitAll().forEach { (pair, aIsWhite, winnerColour) ->
                val tally = head.getOrPut(pair) { DoubleArray(3) }
                when {
                    winnerColour == null -> {
                        tally[2]++
                        points.merge(pair.first, 0.5, Double::plus)
                        points.merge(pair.second, 0.5, Double::plus)
                    }
                    (winnerColour == Colour.WHITE) == aIsWhite -> {
                        tally[0]++
                        points.merge(pair.first, 1.0, Double::plus)
                    }
                    else -> {
                        tally[1]++
                        points.merge(pair.second, 1.0, Double::plus)
                    }
                }
            }
        }

        val gamesEach = gamesPerPairing * (nets.size - 1)
        val mode = fixedDepth?.let { "fixed depth $it" } ?: "${timeMillis}ms clock"
        println("== NNUE ROUND ROBIN ($mode, $gamesPerPairing games per pairing) ==")
        points.entries.sortedByDescending { it.value }.forEach { (net, pts) ->
            val share = pts / gamesEach
            val elo = if (share <= 0.0 || share >= 1.0) "n/a" else "%+.0f".format(-400 * ln(1 / share - 1) / ln(10.0))
            println("   %-16s %5.1f / %d  (%.0f%%, Elo vs field %s)".format(net, pts, gamesEach, share * 100, elo))
        }
        head.forEach { (pair, t) ->
            println("   ${pair.first} vs ${pair.second}: +${t[0].toInt()} -${t[1].toInt()} =${t[2].toInt()}")
        }
    }
}
