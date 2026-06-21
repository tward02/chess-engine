package engine.tournament

import com.tward.engine.game.GameResult
import com.tward.engine.tournament.GameOutcome
import com.tward.engine.tournament.Pairing
import com.tward.engine.tournament.Standings
import engine.tournament.format.spec
import kotlin.test.Test
import kotlin.test.assertEquals

class StandingsTest {

    private val a = spec("A")
    private val b = spec("B")
    private val c = spec("C")
    private val contenders = listOf(a, b, c)

    private fun game(white: com.tward.engine.tournament.BotSpec, black: com.tward.engine.tournament.BotSpec, result: GameResult) =
        GameOutcome(Pairing.game(0, 0, white, black), result)

    @Test
    fun `wins draws and losses are tallied per contender`() {

        val history = listOf(
            game(a, b, GameResult.WHITE_WIN),       // A beats B
            game(c, a, GameResult.DRAW_STALEMATE),  // C draws A
            game(b, c, GameResult.WHITE_WIN)        // B beats C
        )

        val standings = Standings.from(contenders, history)

        val ra = standings.recordFor(a)
        assertEquals(1, ra.wins); assertEquals(1, ra.draws); assertEquals(0, ra.losses)
        assertEquals(1.5, ra.points)

        val rb = standings.recordFor(b)
        assertEquals(1, rb.wins); assertEquals(0, rb.draws); assertEquals(1, rb.losses)
        assertEquals(1.0, rb.points)

        val rc = standings.recordFor(c)
        assertEquals(0, rc.wins); assertEquals(1, rc.draws); assertEquals(1, rc.losses)
        assertEquals(0.5, rc.points)
    }

    @Test
    fun `a bye scores a full point and no game`() {

        val history = listOf(GameOutcome(Pairing.bye(0, 0, a), GameResult.WHITE_WIN))

        val record = Standings.from(contenders, history).recordFor(a)

        assertEquals(1.0, record.points)
        assertEquals(1, record.byes)
        assertEquals(0, record.gamesPlayed)
    }

    @Test
    fun `ranking orders by points then wins then name`() {

        val history = listOf(
            game(a, b, GameResult.BLACK_WIN),  // B beats A -> B 1pt
            game(c, a, GameResult.DRAW_STALEMATE),
            game(b, c, GameResult.DRAW_STALEMATE)
        )
        // Points: B 1.5, C 1.0, A 0.5
        val ranked = Standings.from(contenders, history).ranked

        assertEquals(listOf("B", "C", "A"), ranked.map { it.name })
    }

    @Test
    fun `a time win and a resignation count as decisive results`() {

        val history = listOf(
            game(a, b, GameResult.BLACK_TIME_WIN),   // B wins on time
            game(c, a, GameResult.WHITE_RESIGNATION) // white (C) resigns -> A wins
        )

        val standings = Standings.from(contenders, history)
        assertEquals(1, standings.recordFor(b).wins)
        assertEquals(1, standings.recordFor(a).wins)
        assertEquals(1, standings.recordFor(c).losses)
    }
}
