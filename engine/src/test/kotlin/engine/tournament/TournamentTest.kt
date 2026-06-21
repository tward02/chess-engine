package engine.tournament

import com.tward.engine.board.Move
import com.tward.engine.game.ChessGame
import com.tward.engine.game.GameResult
import com.tward.engine.player.ChessBot
import com.tward.engine.player.bot.RandomBot
import com.tward.engine.tournament.BotSpec
import com.tward.engine.tournament.Tournament
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TournamentTest {

    private fun randomSpec(name: String) = BotSpec(name) { RandomBot() }

    // A bot that records every timeLeft it is given, then plays the first legal move
    private fun recordingSpec(name: String, seen: MutableList<Int>) =
        BotSpec(name) {
            object : ChessBot {
                override fun chooseMove(game: ChessGame, timeLeft: Int): Move {
                    seen.add(timeLeft)
                    return game.getLegalMoves().first()
                }
            }
        }

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

    @Test
    fun `the time budget is passed to the bot, starting at the full amount`() {

        val seen = CopyOnWriteArrayList<Int>()

        val tournament = Tournament(
            recordingSpec("rec", seen),
            randomSpec("opp"),
            totalGames = 1,
            concurrency = 1,
            initialTimeMillis = 60_000
        )

        runBlocking { tournament.runHeadlessWorkers() }

        // The recorder plays White (game 0), so its first move sees the whole budget
        assertEquals(60_000, seen.first())
        // And the remaining time never grows above the budget
        assertTrue(seen.all { it <= 60_000 })
    }

    @Test
    fun `the configured time budget is publicly readable`() {

        // TournamentView reads this to set the displayed game's clock, so it must stay public
        val timed = Tournament(
            randomSpec("A"), randomSpec("B"), totalGames = 1, initialTimeMillis = 180_000
        )
        assertEquals(180_000, timed.initialTimeMillis)

        val untimed = Tournament(randomSpec("A"), randomSpec("B"), totalGames = 1)
        assertEquals(0, untimed.initialTimeMillis)
    }

    @Test
    fun `with no time budget the bot is given zero`() {

        val seen = CopyOnWriteArrayList<Int>()

        val tournament = Tournament(
            recordingSpec("rec", seen),
            randomSpec("opp"),
            totalGames = 1,
            concurrency = 1
            // initialTimeMillis defaults to 0
        )

        runBlocking { tournament.runHeadlessWorkers() }

        assertTrue(seen.isNotEmpty())
        assertTrue(seen.all { it == 0 })
    }

    @Test
    fun `a bot that exceeds its time forfeits`() {

        // White takes far longer than its tiny budget, so it flags on its first move
        val slowWhite = BotSpec("slow") {
            object : ChessBot {
                override fun chooseMove(game: ChessGame, timeLeft: Int): Move {
                    Thread.sleep(50)
                    return game.getLegalMoves().first()
                }
            }
        }

        val tournament = Tournament(
            slowWhite,
            randomSpec("fast"),
            totalGames = 1,
            concurrency = 1,
            initialTimeMillis = 10
        )

        runBlocking { tournament.runHeadlessWorkers() }

        // slow is White (game 0) and flags, so fast (specB) takes the win
        assertEquals(0, tournament.botAWins)
        assertEquals(1, tournament.botBWins)
    }
}
