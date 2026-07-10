package com.tward.engine.tournament

import com.tward.engine.game.GameResult
import com.tward.engine.tournament.format.TournamentFormat
import com.tward.logging.Log
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

/**
 * Runs a multi-contender tournament for any [com.tward.engine.tournament.format.TournamentFormat]. Games of a round are played
 * concurrently on a fixed thread pool; the runner waits for the whole round to be recorded before
 * asking the format for the next round (adaptive formats need every result first).
 *
 * Live display: when [reserveOneForDisplay] is true, one game per round is held back from the
 * worker pool so the UI can drive it on-screen (a random game, since each round's games are
 * shuffled). The UI reads [currentDisplayPairing] and reports its result via [recordDisplayOutcome];
 * the round barrier then waits for that game too. Tests run with it off so they never block on a UI.
 *
 * The [BotSpec]s are the contenders — a fresh bot is built per game, as bots hold per-game state.
 */
class MultiBotTournament(
    val contenders: List<BotSpec>,
    val format: TournamentFormat,
    val concurrency: Int = (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1),
    private val maxPlies: Int = 400,
    val initialTimeMillis: Int = 0,
    val incrementTimeMillis: Int = 0,
    private val reserveOneForDisplay: Boolean = false
) {

    private val log = Log.of<MultiBotTournament>()

    // Every completed game, including byes; the single source of truth for standings and scheduling.
    private val history = CopyOnWriteArrayList<GameOutcome>()

    private val completed = AtomicInteger(0)
    private val currentRoundNumber = AtomicInteger(-1)
    @Volatile private var finished = false

    // Current round's claimable (non-bye) games, shuffled so the displayed game is random.
    @Volatile private var roundPlayable: List<Pairing> = emptyList()
    private val roundClaimIndex = AtomicInteger(0)
    private val roundRecorded = AtomicInteger(0)
    @Volatile private var roundGameTotal = 0

    private val displayPairing = AtomicReference<Pairing?>(null)

    val plannedRounds: Int? = format.plannedRounds(contenders)

    val completedGames: Int get() = completed.get()
    val currentRound: Int get() = currentRoundNumber.get()
    val isComplete: Boolean get() = finished

    /** Live leaderboard, recomputed from history; safe to call from the UI while games run. */
    fun standings(): Standings = Standings.from(contenders, history.toList())

    fun historySnapshot(): List<GameOutcome> = history.toList()

    /** The game reserved for the live view this round, or null if none is reserved/available. */
    fun currentDisplayPairing(): Pairing? = displayPairing.get()

    /** Hands out the next unclaimed game of the current round, or null when the round is drained. */
    fun claimInRound(): Pairing? {
        val i = roundClaimIndex.getAndIncrement()
        return roundPlayable.getOrNull(i)
    }

    /** Records a played (non-bye) game and advances the per-round and overall counters. */
    fun recordOutcome(pairing: Pairing, result: GameResult) {
        history.add(GameOutcome(pairing, result))
        completed.incrementAndGet()
        roundRecorded.incrementAndGet()
    }

    /** The UI reports the result of the game it was driving for display. */
    fun recordDisplayOutcome(pairing: Pairing, result: GameResult) {
        recordOutcome(pairing, result)
    }

    suspend fun runHeadlessWorkers() {

        log.info {
            "Multi-bot tournament starting: ${format.name}, ${contenders.size} contenders " +
                "(${contenders.joinToString { it.name }}) on $concurrency threads"
        }
        val startNanos = System.nanoTime()

        val dispatcher = Executors.newFixedThreadPool(concurrency).asCoroutineDispatcher()

        dispatcher.use { dispatcher ->
            while (true) {
                val pairings = format.nextRound(contenders, history.toList())
                if (pairings.isEmpty()) break

                startRound(pairings)

                coroutineScope {
                    repeat(concurrency) {
                        launch(dispatcher) {
                            while (true) {
                                val pairing = claimInRound() ?: break
                                recordOutcome(pairing, playGame(pairing.white!!, pairing.black!!, maxPlies, initialTimeMillis, incrementTimeMillis))
                            }
                        }
                    }
                }

                awaitRoundComplete()
                log.info { "Round ${currentRoundNumber.get() + 1} complete. ${standingsLine()}" }
            }
        }

        finished = true
        displayPairing.set(null)

        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
        log.info { "Tournament complete in ${elapsedMs}ms. ${standingsLine()}" }
    }

    private fun startRound(pairings: List<Pairing>) {

        currentRoundNumber.set(pairings.first().round)

        // Byes aren't played: score them immediately and keep them out of the claim pool.
        pairings.filter { it.isBye }.forEach { bye ->
            history.add(GameOutcome(bye, GameResult.WHITE_WIN))
            completed.incrementAndGet()
        }

        val playable = pairings.filter { !it.isBye }.shuffled()

        roundGameTotal = playable.size
        roundRecorded.set(0)

        if (reserveOneForDisplay && playable.isNotEmpty()) {
            // Reserve the first (already shuffled, so random) game for the live view; workers take the rest.
            displayPairing.set(playable.first())
            roundClaimIndex.set(1)
        } else {
            displayPairing.set(null)
            roundClaimIndex.set(0)
        }

        roundPlayable = playable
    }

    private suspend fun awaitRoundComplete() {
        // Workers have drained their share; wait for any reserved display game the UI is still driving.
        while (roundRecorded.get() < roundGameTotal) {
            delay(20.milliseconds)
        }
    }

    private fun standingsLine(): String =
        standings().ranked.joinToString(", ") { "${it.name} ${"%.1f".format(it.points)}" }
}
