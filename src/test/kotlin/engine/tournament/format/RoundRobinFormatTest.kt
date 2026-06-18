package engine.tournament.format

import com.tward.engine.tournament.format.RoundRobinFormat
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoundRobinFormatTest {

    private val four = listOf("A", "B", "C", "D").map { spec(it) }

    @Test
    fun `single round-robin schedules every pair exactly once`() {

        val pairings = playRounds(RoundRobinFormat(), four).flatten()

        assertEquals(6, pairings.size)  // C(4,2)
        val met = metPairs(pairings)
        assertEquals(6, met.toSet().size)         // all distinct pairs
        assertEquals(met.size, met.toSet().size)  // none repeated
    }

    @Test
    fun `double round-robin schedules every pair twice with reversed colours`() {

        val pairings = playRounds(RoundRobinFormat(doubleRound = true), four).flatten()

        assertEquals(12, pairings.size)  // 2 * C(4,2)
        // Each unordered pair appears exactly twice
        metPairs(pairings).groupingBy { it }.eachCount().values.forEach { assertEquals(2, it) }

        // Each ordered (white, black) appears once: every pair is played with both colour assignments
        val ordered = pairings.map { it.white!!.name to it.black!!.name }
        assertEquals(ordered.size, ordered.toSet().size)
    }

    @Test
    fun `rounds are balanced and parallelised`() {

        val rounds = playRounds(RoundRobinFormat(), four)

        // 4 contenders -> 3 rounds of 2 simultaneous games
        assertEquals(3, rounds.size)
        rounds.forEach { assertEquals(2, it.size) }
    }

    @Test
    fun `odd field sits one contender out each round without a scored bye`() {

        val three = listOf("A", "B", "C").map { spec(it) }
        val rounds = playRounds(RoundRobinFormat(), three)

        // Every pair still meets once, but no bye pairings are emitted
        assertEquals(3, rounds.flatten().size)
        assertEquals(3, metPairs(rounds.flatten()).toSet().size)
        assertTrue(rounds.flatten().none { it.isBye })
    }

    @Test
    fun `colours are balanced for each contender`() {

        val pairings = playRounds(RoundRobinFormat(), four).flatten()

        four.forEach { contender ->
            val asWhite = pairings.count { it.white === contender }
            val asBlack = pairings.count { it.black === contender }
            assertTrue(abs(asWhite - asBlack) <= 1, "${contender.name}: $asWhite W / $asBlack B")
        }
    }

    @Test
    fun `two contenders play a single game`() {

        val pair = listOf(spec("A"), spec("B"))
        val rounds = playRounds(RoundRobinFormat(), pair)

        assertEquals(1, rounds.size)
        assertEquals(1, rounds.flatten().size)
    }

    @Test
    fun `planned rounds matches the schedule length`() {

        assertEquals(3, RoundRobinFormat().plannedRounds(four))
        assertEquals(6, RoundRobinFormat(doubleRound = true).plannedRounds(four))
    }
}
