package com.tward.engine.tournament

import com.tward.engine.board.Colour
import com.tward.engine.game.GameResult
import com.tward.engine.player.ChessBot
import com.tward.logging.Log
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

// Factory-per-game: bots carry per-game state (opening-book progress) and must not be shared
class BotSpec(
    val name: String,
    val createBot: (Colour) -> ChessBot
)

/**
 * Plays [totalGames] between two bots, alternating colours, on a fixed thread pool.
 * Headless workers and the UI both claim from the same pool, so no game is counted twice.
 */
class Tournament(
    val specA: BotSpec,
    val specB: BotSpec,
    val totalGames: Int,
    val concurrency: Int = (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1),
    private val maxPlies: Int = 400,
    // Per-side time budget in milliseconds, passed to the bots so time-aware bots (e.g. iterative
    // deepening) know how long they have. 0 means "untimed" — bots are given 0 and never flag.
    val initialTimeMillis: Int = 0
) {

    private val log = Log.of<Tournament>()

    private val progressStep = (totalGames / 10).coerceAtLeast(1)

    private val nextIndex = AtomicInteger(0)

    private val aWins = AtomicInteger(0)
    private val bWins = AtomicInteger(0)
    private val draws = AtomicInteger(0)
    private val completed = AtomicInteger(0)
    private val whiteWins = AtomicInteger(0)
    private val blackWins = AtomicInteger(0)

    val botAWins: Int get() = aWins.get()
    val botBWins: Int get() = bWins.get()
    val drawCount: Int get() = draws.get()
    val completedGames: Int get() = completed.get()
    val isComplete: Boolean get() = completed.get() >= totalGames
    val whiteWinsCount: Int get() = whiteWins.get()
    val blackWinsCount: Int get() = blackWins.get()

    /** Returns the next game index to play, or -1 when all games have been claimed. */
    fun claimGameIndex(): Int {
        val index = nextIndex.getAndIncrement()
        return if (index < totalGames) index else -1
    }

    /** Even index: specA plays White. Odd index: specB plays White. */
    fun colourAssignment(index: Int): Pair<BotSpec, BotSpec> {
        return if (index % 2 == 0) specA to specB else specB to specA
    }

    fun record(index: Int, result: GameResult) {
        val aIsWhite = index % 2 == 0

        val whiteWin = isWhiteWin(result)
        when {
            result.isDraw() -> draws.incrementAndGet()
            whiteWin == aIsWhite -> aWins.incrementAndGet()
            else -> bWins.incrementAndGet()
        }

        if (whiteWin) {
            whiteWins.incrementAndGet()
        } else if (!result.isDraw()) {
            blackWins.incrementAndGet()
        }

        val done = completed.incrementAndGet()

        log.debug {
            val white = if (aIsWhite) specA.name else specB.name
            val black = if (aIsWhite) specB.name else specA.name
            "Game $index: $white (white) vs $black (black) -> $result"
        }

        when {
            done >= totalGames -> log.info { "Tournament complete. ${standings()}" }
            done % progressStep == 0 -> log.info { "Progress $done/$totalGames. ${standings()}" }
        }
    }

    fun standings(): String {
        val aScore = botAWins + drawCount * 0.5
        val bScore = botBWins + drawCount * 0.5
        return "${specA.name}: ${botAWins}W/${botBWins}L/${drawCount}D (${"%.1f".format(aScore)} pts), " +
            "${specB.name}: ${botBWins}W/${botAWins}L/${drawCount}D (${"%.1f".format(bScore)} pts)"
    }

    /** Runs all headless games on the thread pool, suspending until complete. */
    suspend fun runHeadlessWorkers() {

        log.info { "Tournament starting: ${specA.name} vs ${specB.name}, $totalGames games on $concurrency threads" }
        val startNanos = System.nanoTime()

        val dispatcher = Executors.newFixedThreadPool(concurrency).asCoroutineDispatcher()

        dispatcher.use { dispatcher ->
            coroutineScope {
                repeat(concurrency) {
                    launch(dispatcher) {
                        while (true) {
                            val index = claimGameIndex()
                            if (index < 0) break
                            record(index, playToEnd(index))
                        }
                    }
                }
            }
        }

        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
        log.info { "Headless games finished in ${elapsedMs}ms" }
    }

    private fun playToEnd(index: Int): GameResult {
        val (whiteSpec, blackSpec) = colourAssignment(index)
        return playGame(whiteSpec, blackSpec, maxPlies, initialTimeMillis)
    }

    private fun isWhiteWin(result: GameResult): Boolean {
        return winner(result) == Colour.WHITE
    }
}
