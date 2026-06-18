package com.tward.engine.tournament.format

import com.tward.engine.tournament.BotSpec
import com.tward.engine.tournament.GameOutcome
import com.tward.engine.tournament.Pairing
import com.tward.engine.tournament.Standings

/**
 * Swiss system: a fixed number of [rounds] with no elimination. Each round pairs contenders with
 * similar scores, avoiding rematches where possible. With an odd field the lowest-ranked contender
 * who has not yet had one receives a bye (scored as a win). Each contender plays at most once per
 * round.
 */
class SwissFormat(private val rounds: Int) : TournamentFormat {

    init {
        require(rounds >= 1) { "Swiss needs at least one round" }
    }

    override val name: String = "Swiss ($rounds rounds)"

    override fun nextRound(contenders: List<BotSpec>, history: List<GameOutcome>): List<Pairing> {

        val round = nextRoundIndex(history)
        if (round >= rounds || contenders.size < 2) return emptyList()

        val standings = Standings.from(contenders, history)

        // Round 1 uses seed order (everyone is on zero); later rounds sort by current score
        val ordered: List<BotSpec> =
            if (round == 0) contenders
            else standings.ranked.map { it.spec }

        val alreadyPlayed = playedPairs(history)
        val hadBye = history.filter { it.pairing.isBye }.map { it.pairing.bye!! }.toSet()

        val pairings = ArrayList<Pairing>()
        var board = 0

        val unpaired = ordered.toMutableList()

        // Odd field: give the bye to the lowest-ranked contender without one yet (else the very last)
        if (unpaired.size % 2 != 0) {
            val byeReceiver = unpaired.lastOrNull { it !in hadBye } ?: unpaired.last()
            unpaired.remove(byeReceiver)
            pairings.add(Pairing.bye(round, board++, byeReceiver))
        }

        while (unpaired.isNotEmpty()) {
            val a = unpaired.removeAt(0)

            // Prefer the nearest-scored opponent that A hasn't met; fall back to the nearest of all
            val opponent =
                unpaired.firstOrNull { setOf(a, it) !in alreadyPlayed } ?: unpaired.first()
            unpaired.remove(opponent)

            // Alternate colours by round to keep the split roughly even
            val (white, black) = if (round % 2 == 0) a to opponent else opponent to a
            pairings.add(Pairing.game(round, board++, white, black))
        }

        return pairings
    }

    override fun plannedRounds(contenders: List<BotSpec>): Int = if (contenders.size < 2) 0 else rounds
}
