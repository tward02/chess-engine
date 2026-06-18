package com.tward.engine.tournament.format

import com.tward.engine.tournament.BotSpec
import com.tward.engine.tournament.GameOutcome
import com.tward.engine.tournament.Pairing

/**
 * Round-robin: every contender plays every other. Schedule is fixed up front (results don't change
 * it) using the circle method, which balances each round so contenders play in parallel.
 *
 * - Single ([doubleRound] = false): n·(n-1)/2 games, each pair meets once.
 * - Double ([doubleRound] = true): n·(n-1) games, each pair meets twice with reversed colours.
 *
 * With an odd number of contenders one sits out each round; round-robin byes are not scored (the
 * contender simply isn't scheduled that round), so no bye pairing is emitted.
 */
class RoundRobinFormat(private val doubleRound: Boolean = false) : TournamentFormat {

    override val name: String = if (doubleRound) "Double Round-Robin" else "Round-Robin"

    override fun nextRound(contenders: List<BotSpec>, history: List<GameOutcome>): List<Pairing> {
        val schedule = buildSchedule(contenders)
        val round = nextRoundIndex(history)
        return schedule.getOrElse(round) { emptyList() }
    }

    override fun plannedRounds(contenders: List<BotSpec>): Int = buildSchedule(contenders).size

    private fun buildSchedule(contenders: List<BotSpec>): List<List<Pairing>> {
        if (contenders.size < 2) return emptyList()

        val firstLeg = circleMethod(contenders)
        if (!doubleRound) return firstLeg

        // Second leg replays every game with colours reversed: each pair meets twice, once with
        // each colour assignment, and every contender's colours come out perfectly balanced.
        val secondLeg = firstLeg.mapIndexed { r, round ->
            round.map { Pairing.game(firstLeg.size + r, it.board, it.black!!, it.white!!) }
        }
        return firstLeg + secondLeg
    }

    /**
     * Classic circle method: a phantom is added for an odd field, index 0 stays fixed while the rest
     * rotate. Within each round the contender who has had White fewer times so far takes White, which
     * keeps every contender's colour split balanced to within one game.
     */
    private fun circleMethod(contenders: List<BotSpec>): List<List<Pairing>> {

        val n = contenders.size
        // Use indices, with -1 as the phantom that produces an (unscored) sit-out
        val odd = n % 2 != 0
        val slots = ArrayDeque((0 until n).toMutableList().also { if (odd) it.add(-1) })
        val size = slots.size
        val roundsCount = size - 1

        val whiteCount = IntArray(n)
        val rounds = ArrayList<List<Pairing>>(roundsCount)

        for (r in 0 until roundsCount) {
            val pairings = ArrayList<Pairing>(size / 2)
            var board = 0

            for (i in 0 until size / 2) {
                val home = slots[i]
                val away = slots[size - 1 - i]
                if (home == -1 || away == -1) continue  // phantom: that contender sits out

                val white: Int
                val black: Int
                when {
                    whiteCount[home] < whiteCount[away] -> { white = home; black = away }
                    whiteCount[home] > whiteCount[away] -> { white = away; black = home }
                    // Tie: alternate by round and board so neither contender is favoured
                    (r + i) % 2 == 0 -> { white = home; black = away }
                    else -> { white = away; black = home }
                }
                whiteCount[white]++

                pairings.add(Pairing.game(r, board++, contenders[white], contenders[black]))
            }

            rounds.add(pairings)

            // Rotate: keep slot 0 fixed, move the last slot to position 1
            val last = slots.removeLast()
            slots.add(1, last)
        }

        return rounds
    }
}
