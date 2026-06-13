package engine.tournament

import com.tward.engine.game.GameResult
import com.tward.engine.player.bot.RandomBot
import com.tward.engine.tournament.BotSpec
import com.tward.engine.tournament.Tournament
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TournamentTest {

    private fun randomSpec(name: String) = BotSpec(name) { RandomBot() }

    private fun tournament() =
        Tournament(randomSpec("A"), randomSpec("B"), totalGames = 10)

    @Test
    fun `every game is played and counted exactly once`() {

        val tournament =
            Tournament(
                randomSpec("Random A"),
                randomSpec("Random B"),
                totalGames = 40,
                concurrency = 4
            )

        runBlocking { tournament.runHeadlessWorkers() }

        assertEquals(40, tournament.completedGames)
        assertEquals(
            40,
            tournament.botAWins + tournament.botBWins + tournament.drawCount
        )
        assertTrue(tournament.isComplete)
    }

    @Test
    fun `claiming hands out each index once then returns minus one`() {

        val tournament =
            Tournament(randomSpec("A"), randomSpec("B"), totalGames = 5)

        val claimed = generateSequence { tournament.claimGameIndex() }
            .takeWhile { it >= 0 }
            .toList()

        assertEquals(listOf(0, 1, 2, 3, 4), claimed.sorted())
        assertEquals(-1, tournament.claimGameIndex())
    }

    @Test
    fun `contenders alternate colours by index`() {

        val tournament = tournament()

        // Even index: A is white. Odd index: B is white.
        assertEquals(tournament.specA, tournament.colourAssignment(0).first)
        assertEquals(tournament.specB, tournament.colourAssignment(0).second)

        assertEquals(tournament.specB, tournament.colourAssignment(1).first)
        assertEquals(tournament.specA, tournament.colourAssignment(1).second)
    }

    @Test
    fun `white win on an even game counts for bot A`() {

        val tournament = tournament()
        tournament.record(index = 0, result = GameResult.WHITE_WIN)

        assertEquals(1, tournament.botAWins)
        assertEquals(0, tournament.botBWins)
    }

    @Test
    fun `white win on an odd game counts for bot B`() {

        val tournament = tournament()
        // On odd games B plays white, so a white win is a B win
        tournament.record(index = 1, result = GameResult.WHITE_WIN)

        assertEquals(0, tournament.botAWins)
        assertEquals(1, tournament.botBWins)
    }

    @Test
    fun `black win on an even game counts for bot B`() {

        val tournament = tournament()
        tournament.record(index = 0, result = GameResult.BLACK_WIN)

        assertEquals(0, tournament.botAWins)
        assertEquals(1, tournament.botBWins)
    }

    @Test
    fun `draws are counted for neither bot`() {

        val tournament = tournament()
        tournament.record(index = 0, result = GameResult.DRAW_STALEMATE)

        assertEquals(0, tournament.botAWins)
        assertEquals(0, tournament.botBWins)
        assertEquals(1, tournament.drawCount)
        assertEquals(1, tournament.completedGames)
    }
}
