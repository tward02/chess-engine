package com.tward.monitor

import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class StatusPollerTest {

    private val now = Instant.parse("2026-11-01T18:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val today = now.toEpochMilli()
    private val yesterday = now.minusSeconds(86_400).toEpochMilli()

    private fun game(id: String, outcome: Outcome, endedAt: Long, diff: Int? = null, rated: Boolean = true) =
        RecentGame(id, "u/$id", "white", "opp", 1500, "blitz", rated, outcome, "mate", diff, endedAt)

    private class FakeSource(
        var presence: Presence = Presence(online = true, playing = false),
        var live: List<LiveGame>? = emptyList(),
        var games: List<RecentGame> = emptyList(),
    ) : LichessSource {
        override suspend fun profile() = Profile(mapOf("blitz" to PerfRating(1800, 10, false, 5)))
        override suspend fun presence() = presence
        override suspend fun recentGames(max: Int) = games
        override suspend fun playing() = live
    }

    private val liveGame = LiveGame(
        id = "g", url = "u", colour = "black", opponent = "Human", opponentRating = 1900, speed = "blitz",
        rated = true, fen = "4k3/8/8/8/8/8/8/3QK3 b - - 0 1", lastMove = null, botToMove = true, botSecondsLeft = 60,
    )

    private fun poller(source: LichessSource, process: Boolean? = true, token: String? = "t") =
        StatusPoller(MonitorConfig(bot = "TBot", token = token), source, processProbe = { process }, clock = clock)

    @Test
    fun `summarises only today's finished games`() {
        val summary = StatusPoller.summariseDay(
            listOf(
                game("a", Outcome.WIN, today, 8),
                game("b", Outcome.LOSS, today, -5),
                game("c", Outcome.DRAW, today, 0, rated = false),
                game("d", Outcome.ABORTED, today),
                game("e", Outcome.WIN, yesterday, 9),
            ),
            LocalDate.of(2026, 11, 1), ZoneOffset.UTC,
        )
        assertEquals(DaySummary(games = 3, wins = 1, losses = 1, draws = 1, ratingChange = 3), summary)
    }

    @Test
    fun `state follows process, live games and presence`() = runBlocking {
        val source = FakeSource()
        val poller = poller(source)
        assertEquals(BotState.STARTING, poller.status.value.state)

        poller.refreshProfile()
        poller.refreshLive()
        assertEquals(BotState.IDLE, poller.status.value.state)

        source.live = listOf(liveGame)
        poller.refreshLive()
        assertEquals(BotState.PLAYING, poller.status.value.state)

        source.live = emptyList()
        source.presence = Presence(online = false, playing = false)
        poller.refreshProfile()
        poller.refreshLive()
        assertEquals(BotState.OFFLINE, poller.status.value.state)

        assertEquals(BotState.DOWN, poller(source, process = false).apply { refreshLive() }.status.value.state)
    }

    @Test
    fun `without a token, presence decides whether the bot is playing`() = runBlocking {
        val poller = poller(FakeSource(presence = Presence(online = true, playing = true), live = null), token = null)
        poller.refreshProfile()
        poller.refreshLive()
        assertEquals(BotState.PLAYING, poller.status.value.state)
        assertEquals(emptyList(), poller.status.value.liveGames)
    }

    @Test
    fun `live games get an eval from the bot's side`() = runBlocking {
        val poller = poller(FakeSource(live = listOf(liveGame)))
        poller.refreshLive()
        // White is a queen up and the bot is Black, so the eval is strongly negative.
        val eval = assertNotNull(poller.status.value.liveGames.single().evalCp)
        assert(eval < -500) { "expected a big deficit, got $eval" }
    }
}
