package com.tward.monitor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration.Companion.milliseconds

/**
 * Polls Lichess one request at a time (as its API guidelines ask) and folds the results into a
 * single [MonitorStatus]. Live games are polled fast while a game is on and slower when idle;
 * profile, presence and recent games every [MonitorConfig.profilePollSeconds].
 */
class StatusPoller(
    private val config: MonitorConfig,
    private val source: LichessSource,
    private val processProbe: () -> Boolean? = { ProcessProbe.isRunning(config.processPattern) },
    private val clock: Clock = Clock.systemDefaultZone(),
    private val quickEval: QuickEval = QuickEval(),
) {
    private val log = LoggerFactory.getLogger(StatusPoller::class.java)

    private val _status = MutableStateFlow(MonitorStatus(bot = config.bot, liveGamesAvailable = config.token != null))
    val status: StateFlow<MonitorStatus> = _status.asStateFlow()

    private var presence: Presence? = null

    suspend fun refreshProfile() {
        val profile = source.profile()
        val presence = source.presence().also { this.presence = it }
        val recent = source.recentGames(RECENT_FETCH)
        _status.update {
            it.copy(
                ratings = profile.ratings,
                lichessOnline = presence.online,
                recentGames = recent.filter { g -> g.outcome != Outcome.ABORTED }.take(RECENT_SHOWN),
                today = summariseDay(recent, LocalDate.now(clock), clock.zone),
            ).withDerivedState()
        }
    }

    suspend fun refreshLive() {
        val processRunning = processProbe()
        val live = source.playing()?.map(quickEval::forBot)
        _status.update {
            it.copy(
                processRunning = processRunning,
                liveGames = live ?: emptyList(),
                updatedAt = clock.millis(),
                lastError = null,
            ).withDerivedState()
        }
    }

    fun start(scope: CoroutineScope): Job = scope.launch(Dispatchers.IO) {
        var nextProfileAt = 0L
        while (isActive) {
            val wait = try {
                if (clock.millis() >= nextProfileAt) {
                    refreshProfile()
                    nextProfileAt = clock.millis() + config.profilePollSeconds * 1000
                }
                refreshLive()
                if (_status.value.liveGames.isNotEmpty()) config.livePollSeconds else config.idlePollSeconds
            } catch (e: RateLimitedException) {
                recordError(e)
                60
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                recordError(e)
                config.idlePollSeconds
            }
            delay((wait * 1000).milliseconds)
        }
    }

    private fun recordError(e: Exception) {
        log.warn("Poll failed: {}", e.message)
        _status.update { it.copy(lastError = e.message ?: e::class.simpleName, processRunning = processProbe()).withDerivedState() }
    }

    private fun MonitorStatus.withDerivedState(): MonitorStatus = copy(
        state = when {
            processRunning == false -> BotState.DOWN
            liveGames.isNotEmpty() || (!liveGamesAvailable && presence?.playing == true) -> BotState.PLAYING
            lichessOnline == null -> BotState.STARTING
            lichessOnline == false -> BotState.OFFLINE
            else -> BotState.IDLE
        }
    )

    companion object {
        /** Fetched to count today's results; a bot playing more games than this a day undercounts. */
        const val RECENT_FETCH = 50
        const val RECENT_SHOWN = 15

        fun summariseDay(games: List<RecentGame>, day: LocalDate, zone: ZoneId): DaySummary {
            val todays = games.filter {
                it.outcome != Outcome.ABORTED &&
                    java.time.Instant.ofEpochMilli(it.endedAt).atZone(zone).toLocalDate() == day
            }
            return DaySummary(
                games = todays.size,
                wins = todays.count { it.outcome == Outcome.WIN },
                losses = todays.count { it.outcome == Outcome.LOSS },
                draws = todays.count { it.outcome == Outcome.DRAW },
                ratingChange = todays.filter { it.rated }.sumOf { it.ratingDiff ?: 0 },
            )
        }
    }
}

object ProcessProbe {
    /** Whether any process's command line contains [pattern]; null when checking is disabled. */
    fun isRunning(pattern: String?): Boolean? {
        if (pattern == null) return null
        return ProcessHandle.allProcesses().anyMatch { handle ->
            handle.info().commandLine().map { it.contains(pattern) }.orElse(false)
        }
    }
}
