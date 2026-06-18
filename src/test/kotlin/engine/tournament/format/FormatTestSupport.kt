package engine.tournament.format

import com.tward.engine.game.GameResult
import com.tward.engine.player.bot.RandomBot
import com.tward.engine.tournament.BotSpec
import com.tward.engine.tournament.GameOutcome
import com.tward.engine.tournament.Pairing
import com.tward.engine.tournament.format.TournamentFormat

/** A named contender; createBot is never invoked by the pure-format tests. */
fun spec(name: String) = BotSpec(name) { RandomBot() }

/** All contender pairs that met across [pairings], as unordered name pairs (ignores byes). */
fun metPairs(pairings: List<Pairing>): List<Set<String>> =
    pairings.filterNot { it.isBye }.map { setOf(it.white!!.name, it.black!!.name) }

/**
 * Drives [format] to completion, feeding each game a result from [resultFor], and returns the
 * pairings round by round. Caps iterations so a buggy adaptive format can't loop forever.
 */
fun playRounds(
    format: TournamentFormat,
    contenders: List<BotSpec>,
    resultFor: (Pairing) -> GameResult = { GameResult.DRAW_STALEMATE }
): List<List<Pairing>> {

    val history = mutableListOf<GameOutcome>()
    val rounds = mutableListOf<List<Pairing>>()

    while (true) {
        val round = format.nextRound(contenders, history)
        if (round.isEmpty()) break
        rounds.add(round)
        round.forEach { p ->
            val result = if (p.isBye) GameResult.WHITE_WIN else resultFor(p)
            history.add(GameOutcome(p, result))
        }
        check(rounds.size <= 1000) { "format did not terminate" }
    }

    return rounds
}

/** A result function under which the contender appearing earlier in [order] always wins. */
fun higherSeedWins(order: List<BotSpec>): (Pairing) -> GameResult = { p ->
    if (order.indexOf(p.white) < order.indexOf(p.black)) GameResult.WHITE_WIN
    else GameResult.BLACK_WIN
}
