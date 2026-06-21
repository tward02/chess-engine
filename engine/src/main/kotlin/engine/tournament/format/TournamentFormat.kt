package com.tward.engine.tournament.format

import com.tward.engine.tournament.BotSpec
import com.tward.engine.tournament.GameOutcome
import com.tward.engine.tournament.Pairing

/**
 * A tournament format decides which games are played, round by round.
 *
 * [nextRound] is a pure function of the contenders and the full history of completed games so far:
 * it returns the pairings for the next round, or an empty list when the tournament is finished.
 * Static formats (round-robin) ignore results; adaptive formats (knockout, Swiss) use them to
 * decide who plays whom next. The runner plays one full round before calling [nextRound] again.
 */
interface TournamentFormat {

    val name: String

    fun nextRound(contenders: List<BotSpec>, history: List<GameOutcome>): List<Pairing>

    /** Total rounds when known up front (for progress display), or null if it can't be predicted. */
    fun plannedRounds(contenders: List<BotSpec>): Int? = null
}

/** The index of the round to schedule next, given everything played so far. */
internal fun nextRoundIndex(history: List<GameOutcome>): Int =
    if (history.isEmpty()) 0 else history.maxOf { it.pairing.round } + 1

/** Unordered set of contender pairs that have already met (ignores byes). */
internal fun playedPairs(history: List<GameOutcome>): Set<Set<BotSpec>> =
    history.asSequence()
        .filterNot { it.pairing.isBye }
        .map { setOf(it.pairing.white!!, it.pairing.black!!) }
        .toSet()
