package com.tward.engine.tournament

import com.tward.engine.board.Colour

/** A single contender's tally. [points] scores a win as 1.0, a draw as 0.5, a bye as 1.0. */
data class ContenderRecord(
    val spec: BotSpec,
    val wins: Int = 0,
    val draws: Int = 0,
    val losses: Int = 0,
    val byes: Int = 0
) {
    val gamesPlayed: Int get() = wins + draws + losses
    val points: Double get() = wins + byes + draws * 0.5

    val name: String get() = spec.name
}

/**
 * A ranked leaderboard derived purely from a list of [GameOutcome]s — there is no mutable shared
 * state, so it can be recomputed at any time (the live UI does this on a poll loop).
 */
class Standings private constructor(val records: List<ContenderRecord>) {

    /** Records ordered best-first: points, then wins, then name. */
    val ranked: List<ContenderRecord> =
        records.sortedWith(
            compareByDescending<ContenderRecord> { it.points }
                .thenByDescending { it.wins }
                .thenBy { it.name }
        )

    fun recordFor(spec: BotSpec): ContenderRecord =
        records.first { it.spec === spec }

    companion object {

        fun from(contenders: List<BotSpec>, history: List<GameOutcome>): Standings {

            val wins = HashMap<BotSpec, Int>()
            val draws = HashMap<BotSpec, Int>()
            val losses = HashMap<BotSpec, Int>()
            val byes = HashMap<BotSpec, Int>()

            fun bump(map: HashMap<BotSpec, Int>, spec: BotSpec) {
                map[spec] = (map[spec] ?: 0) + 1
            }

            for (outcome in history) {
                val p = outcome.pairing

                if (p.isBye) {
                    bump(byes, p.bye!!)
                    continue
                }

                val white = p.white!!
                val black = p.black!!

                when (winner(outcome.result)) {
                    Colour.WHITE -> { bump(wins, white); bump(losses, black) }
                    Colour.BLACK -> { bump(wins, black); bump(losses, white) }
                    null -> { bump(draws, white); bump(draws, black) }
                }
            }

            val records = contenders.map { spec ->
                ContenderRecord(
                    spec = spec,
                    wins = wins[spec] ?: 0,
                    draws = draws[spec] ?: 0,
                    losses = losses[spec] ?: 0,
                    byes = byes[spec] ?: 0
                )
            }

            return Standings(records)
        }
    }
}
