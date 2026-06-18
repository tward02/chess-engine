package engine.tournament.format

import com.tward.engine.game.GameResult
import com.tward.engine.tournament.BotSpec
import com.tward.engine.tournament.Pairing
import com.tward.engine.tournament.format.KnockoutFormat
import com.tward.engine.tournament.format.KnockoutTiebreak
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KnockoutFormatTest {

    private fun field(n: Int) = (0 until n).map { spec("S$it") }

    // Winner of the final game, decided by which seed advances (earlier index = higher seed).
    private fun champion(rounds: List<List<Pairing>>, order: List<BotSpec>, higherSeed: Boolean): BotSpec {
        val finalGame = rounds.last().first { !it.isBye }
        val whiteIsHigher = order.indexOf(finalGame.white) < order.indexOf(finalGame.black)
        val higher = if (whiteIsHigher) finalGame.white!! else finalGame.black!!
        val lower = if (whiteIsHigher) finalGame.black!! else finalGame.white!!
        return if (higherSeed) higher else lower
    }

    @Test
    fun `power-of-two field halves each round to a champion`() {

        val order = field(8)
        val rounds = playRounds(KnockoutFormat(), order, higherSeedWins(order))

        assertEquals(3, rounds.size)                        // 8 -> 4 -> 2 -> 1
        assertEquals(7, rounds.flatten().count { !it.isBye }) // 4 + 2 + 1 games
        assertTrue(rounds.flatten().none { it.isBye })       // no byes for a power-of-two field
        assertEquals(order.first(), champion(rounds, order, higherSeed = true))
    }

    @Test
    fun `top seed advances on a bye when the field is odd`() {

        val order = field(5)
        val rounds = playRounds(KnockoutFormat(), order, higherSeedWins(order))

        assertEquals(3, rounds.size)  // ceil(log2 5)
        assertTrue(rounds.first().any { it.isBye })
        assertEquals(order.first(), rounds.first().first { it.isBye }.bye)  // top seed gets the bye
        assertEquals(order.first(), champion(rounds, order, higherSeed = true))
    }

    @Test
    fun `two contenders play a single final`() {

        val order = field(2)
        val rounds = playRounds(KnockoutFormat(), order, higherSeedWins(order))

        assertEquals(1, rounds.size)
        assertEquals(1, rounds.flatten().count { !it.isBye })
    }

    @Test
    fun `draws are broken towards the higher seed by default`() {

        val order = field(4)
        val rounds = playRounds(KnockoutFormat(), order) { GameResult.DRAW_STALEMATE }

        assertEquals(order.first(), champion(rounds, order, higherSeed = true))
    }

    @Test
    fun `the lower-seed tiebreak sends the lower seed through`() {

        val order = field(4)
        val format = KnockoutFormat(tiebreak = KnockoutTiebreak.LOWER_SEED)
        val rounds = playRounds(format, order) { GameResult.DRAW_STALEMATE }

        assertEquals(order.last(), champion(rounds, order, higherSeed = false))
    }

    @Test
    fun `planned rounds matches ceil log2 of the field`() {
        assertEquals(2, KnockoutFormat().plannedRounds(field(4)))
        assertEquals(3, KnockoutFormat().plannedRounds(field(5)))
        assertEquals(3, KnockoutFormat().plannedRounds(field(8)))
    }
}
