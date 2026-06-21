package engine.tournament

import com.tward.engine.board.Move
import com.tward.engine.game.ChessGame
import com.tward.engine.player.ChessBot
import com.tward.engine.player.bot.RandomBot
import com.tward.engine.tournament.BotSpec
import com.tward.engine.tournament.MultiBotTournament
import com.tward.engine.tournament.format.KnockoutFormat
import com.tward.engine.tournament.format.RoundRobinFormat
import com.tward.engine.tournament.format.SwissFormat
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MultiBotTournamentTest {

    private fun randoms(vararg names: String) = names.map { name -> BotSpec(name) { RandomBot() } }

    // Points are conserved: every game (and bye) adds exactly one point to the pool.
    private fun assertStandingsConsistent(tournament: MultiBotTournament) {
        val ranked = tournament.standings().ranked
        val wins = ranked.sumOf { it.wins }
        val losses = ranked.sumOf { it.losses }
        assertEquals(wins, losses, "each decisive game is one win and one loss")
        assertEquals(tournament.completedGames.toDouble(), ranked.sumOf { it.points })
    }

    @Test
    fun `round-robin plays every scheduled game exactly once and completes`() {

        val tournament = MultiBotTournament(
            contenders = randoms("A", "B", "C", "D"),
            format = RoundRobinFormat(),
            concurrency = 4
        )

        runBlocking { tournament.runHeadlessWorkers() }

        assertTrue(tournament.isComplete)
        assertEquals(6, tournament.completedGames)  // C(4,2)

        val pairs = tournament.historySnapshot()
            .map { setOf(it.pairing.white!!.name, it.pairing.black!!.name) }
        assertEquals(pairs.size, pairs.toSet().size, "a pairing was played more than once")

        assertStandingsConsistent(tournament)
    }

    @Test
    fun `double round-robin plays twice as many games`() {

        val tournament = MultiBotTournament(
            contenders = randoms("A", "B", "C", "D"),
            format = RoundRobinFormat(doubleRound = true),
            concurrency = 4
        )

        runBlocking { tournament.runHeadlessWorkers() }

        assertEquals(12, tournament.completedGames)
        assertStandingsConsistent(tournament)
    }

    @Test
    fun `knockout runs to a single champion`() {

        val tournament = MultiBotTournament(
            contenders = randoms("A", "B", "C", "D"),
            format = KnockoutFormat()
        )

        runBlocking { tournament.runHeadlessWorkers() }

        assertTrue(tournament.isComplete)
        assertEquals(3, tournament.completedGames)  // 2 semis + 1 final, no byes for a field of 4
        assertStandingsConsistent(tournament)
    }

    @Test
    fun `swiss runs the configured number of rounds`() {

        val tournament = MultiBotTournament(
            contenders = randoms("A", "B", "C", "D"),
            format = SwissFormat(rounds = 3)
        )

        runBlocking { tournament.runHeadlessWorkers() }

        assertTrue(tournament.isComplete)
        assertEquals(6, tournament.completedGames)  // 3 rounds * 2 games
        assertStandingsConsistent(tournament)
    }

    @Test
    fun `the time budget is passed to the bots starting at the full amount`() {

        val seen = CopyOnWriteArrayList<Int>()
        val recorder = BotSpec("rec") {
            object : ChessBot {
                override fun chooseMove(game: ChessGame, timeLeft: Int): Move {
                    seen.add(timeLeft)
                    return game.getLegalMoves().first()
                }
            }
        }

        val tournament = MultiBotTournament(
            contenders = listOf(recorder, BotSpec("opp") { RandomBot() }),
            format = RoundRobinFormat(),
            concurrency = 1,
            initialTimeMillis = 60_000
        )

        runBlocking { tournament.runHeadlessWorkers() }

        assertTrue(seen.isNotEmpty())
        assertTrue(seen.all { it <= 60_000 })
        assertTrue(seen.contains(60_000))  // a first move saw the whole budget
    }
}
