package com.tward.engine.tournament.format

import com.tward.engine.board.Colour
import com.tward.engine.tournament.BotSpec
import com.tward.engine.tournament.GameOutcome
import com.tward.engine.tournament.Pairing
import com.tward.engine.tournament.winner

/**
 * How a drawn knockout game is resolved. Bots draw often and can be near-deterministic, so a replay
 * could loop forever; instead a draw is broken by seed (position in the original contender list),
 * which is deterministic and always terminates.
 */
enum class KnockoutTiebreak {
    /** The contender appearing earlier in the contenders list advances. */
    HIGHER_SEED,

    /** The contender appearing later in the contenders list advances. */
    LOWER_SEED
}

/**
 * Single-elimination knockout. Contenders are seeded by their order in the list. Each round the
 * alive contenders are paired (top seed gets a bye when the count is odd) and winners advance; the
 * tournament ends when one contender remains. Draws are resolved by [tiebreak].
 */
class KnockoutFormat(
    private val tiebreak: KnockoutTiebreak = KnockoutTiebreak.HIGHER_SEED
) : TournamentFormat {

    override val name: String = "Knockout"

    override fun nextRound(contenders: List<BotSpec>, history: List<GameOutcome>): List<Pairing> {

        val seed = contenders.withIndex().associate { (i, spec) -> spec to i }

        var alive = contenders
        var round = 0
        while (true) {
            val roundOutcomes = history.filter { it.pairing.round == round }
            if (roundOutcomes.isEmpty()) break
            alive = advance(roundOutcomes, seed)
            round++
        }

        if (alive.size < 2) return emptyList()

        return pairRound(alive, round)
    }

    override fun plannedRounds(contenders: List<BotSpec>): Int {
        // Each round halves the field, so ⌈log2 n⌉ rounds crown a champion.
        var remaining = contenders.size
        var rounds = 0
        while (remaining > 1) {
            remaining = (remaining + 1) / 2
            rounds++
        }
        return rounds
    }

    /** Winners (and bye recipients) of a completed round, preserving seed order. */
    private fun advance(roundOutcomes: List<GameOutcome>, seed: Map<BotSpec, Int>): List<BotSpec> =
        roundOutcomes
            .sortedBy { it.pairing.board }
            .map { outcome ->
                val p = outcome.pairing
                if (p.isBye) {
                    p.bye!!
                } else {
                    when (winner(outcome.result)) {
                        Colour.WHITE -> p.white!!
                        Colour.BLACK -> p.black!!
                        null -> breakTie(p.white!!, p.black!!, seed)
                    }
                }
            }

    private fun breakTie(white: BotSpec, black: BotSpec, seed: Map<BotSpec, Int>): BotSpec {
        val whiteSeed = seed.getValue(white)
        val blackSeed = seed.getValue(black)
        val higher = if (whiteSeed < blackSeed) white else black
        val lower = if (whiteSeed < blackSeed) black else white
        return if (tiebreak == KnockoutTiebreak.HIGHER_SEED) higher else lower
    }

    private fun pairRound(alive: List<BotSpec>, round: Int): List<Pairing> {
        val pairings = ArrayList<Pairing>()
        var board = 0

        // Odd field: the top remaining seed advances on a bye
        var rest = alive
        if (alive.size % 2 != 0) {
            pairings.add(Pairing.bye(round, board++, alive.first()))
            rest = alive.drop(1)
        }

        var i = 0
        while (i < rest.size) {
            // Alternate colours by round so a contender isn't always the same colour
            val a = rest[i]
            val b = rest[i + 1]
            val (white, black) = if (round % 2 == 0) a to b else b to a
            pairings.add(Pairing.game(round, board++, white, black))
            i += 2
        }

        return pairings
    }
}
