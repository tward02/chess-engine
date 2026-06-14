package com.tward.engine.tournament

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.game.ChessGame
import com.tward.engine.game.GameResult
import com.tward.engine.player.ChessBot
import com.tward.logging.Log
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

// A named contender. The factory makes a fresh bot for a colour, because bots carry
// per-game state (e.g. opening-book progress) and so must not be shared between games
class BotSpec(
    val name: String,
    val createBot: (Colour) -> ChessBot
)

/**
 * Plays [totalGames] between two bots, alternating colours, running [concurrency] games
 * at once on a dedicated thread pool. One game can be played through the UI in parallel;
 * both headless workers and the UI claim games from the same pool and report into the
 * same thread-safe tally, so no game is played or counted twice.
 */
class Tournament(
    val specA: BotSpec,
    val specB: BotSpec,
    val totalGames: Int,
    val concurrency: Int = (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1),
    private val maxPlies: Int = 400
) {

    private val log = Log.of<Tournament>()

    // Log a progress line roughly every 10% of the run
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

    // Claims the next game to play, or -1 once every game has been handed out
    fun claimGameIndex(): Int {
        val index = nextIndex.getAndIncrement()
        return if (index < totalGames) index else -1
    }

    // Contenders alternate colours by game index so each plays White half the time
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

    // Runs the headless games across the thread pool, suspending until they are all done
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
        val whiteBot = whiteSpec.createBot(Colour.WHITE)
        val blackBot = blackSpec.createBot(Colour.BLACK)

        val game = ChessGame(Board.getStartingBoard())

        var plies = 0
        while (plies < maxPlies) {
            val result = game.getGameResult()
            if (result != null) return result

            val bot = if (game.board.activeColour == Colour.WHITE) whiteBot else blackBot
            game.makeMove(bot.chooseMove(game))
            plies++
        }

        // A game that never resolves within the ply cap is adjudicated a draw
        return GameResult.DRAW_50_MOVE_RULE
    }

    private fun isWhiteWin(result: GameResult): Boolean {
        return result == GameResult.WHITE_WIN || result == GameResult.WHITE_TIME_WIN
    }
}
