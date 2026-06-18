package engine.tournament.format

import com.tward.engine.game.GameResult
import com.tward.engine.tournament.Pairing
import com.tward.engine.tournament.format.SwissFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SwissFormatTest {

    private val four = listOf("A", "B", "C", "D").map { spec(it) }

    private fun contendersIn(round: List<Pairing>): List<String> =
        round.flatMap { p ->
            if (p.isBye) listOf(p.bye!!.name) else listOf(p.white!!.name, p.black!!.name)
        }

    @Test
    fun `runs the configured number of rounds`() {

        val rounds = playRounds(SwissFormat(rounds = 3), four)

        assertEquals(3, rounds.size)
        assertEquals(3, SwissFormat(rounds = 3).plannedRounds(four))
    }

    @Test
    fun `each contender plays at most once per round`() {

        val rounds = playRounds(SwissFormat(rounds = 3), four)

        rounds.forEach { round ->
            val names = contendersIn(round)
            assertEquals(names.size, names.toSet().size, "a contender appears twice in a round")
            assertTrue(names.toSet().containsAll(four.map { it.name }))
        }
    }

    @Test
    fun `round one pairs by seed order`() {

        val first = playRounds(SwissFormat(rounds = 1), four).first()

        assertEquals(setOf("A", "B"), setOf(first[0].white!!.name, first[0].black!!.name))
        assertEquals(setOf("C", "D"), setOf(first[1].white!!.name, first[1].black!!.name))
    }

    @Test
    fun `later rounds pair contenders on equal scores and avoid rematches`() {

        // White always wins: after round 1, A and C have a point; B and D have none.
        val rounds = playRounds(SwissFormat(rounds = 2), four) { GameResult.WHITE_WIN }

        val second = rounds[1].map { setOf(it.white!!.name, it.black!!.name) }
        assertTrue(setOf("A", "C") in second, "round 2 should pair the two winners")
        assertTrue(setOf("B", "D") in second, "round 2 should pair the two losers")

        // No pair is repeated across the two rounds
        val allMet = metPairs(rounds.flatten())
        assertEquals(allMet.size, allMet.toSet().size)
    }

    @Test
    fun `odd field gives one bye per round to distinct contenders`() {

        val five = listOf("A", "B", "C", "D", "E").map { spec(it) }
        val rounds = playRounds(SwissFormat(rounds = 3), five)

        val byeNames = rounds.flatten().filter { it.isBye }.map { it.bye!!.name }
        assertEquals(3, byeNames.size)                       // one bye each round
        assertEquals(byeNames.size, byeNames.toSet().size)   // no contender byes twice
        rounds.forEach { assertEquals(2, it.count { p -> !p.isBye }) }  // 2 games + 1 bye
    }
}
