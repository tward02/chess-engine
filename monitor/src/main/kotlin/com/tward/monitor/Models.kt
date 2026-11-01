package com.tward.monitor

import kotlinx.serialization.Serializable

/** Everything the dashboard and Home Assistant show. Served as JSON at `/api/status`. */
@Serializable
data class MonitorStatus(
    val bot: String,
    val state: BotState = BotState.STARTING,
    /** Null until the first Lichess fetch succeeds. */
    val lichessOnline: Boolean? = null,
    /** Null when process checking is disabled. */
    val processRunning: Boolean? = null,
    val ratings: Map<String, PerfRating> = emptyMap(),
    val today: DaySummary = DaySummary(),
    val liveGames: List<LiveGame> = emptyList(),
    val recentGames: List<RecentGame> = emptyList(),
    /** False when no token is configured, so live games can't be listed. */
    val liveGamesAvailable: Boolean = false,
    val updatedAt: Long? = null,
    val lastError: String? = null,
)

@Serializable
enum class BotState { STARTING, PLAYING, IDLE, OFFLINE, DOWN }

@Serializable
data class PerfRating(val rating: Int, val games: Int, val provisional: Boolean, val progress: Int)

@Serializable
data class DaySummary(
    val games: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    /** Sum of rating changes over today's rated games, all speeds. */
    val ratingChange: Int = 0,
)

@Serializable
data class LiveGame(
    val id: String,
    val url: String,
    val colour: String,
    val opponent: String,
    val opponentRating: Int?,
    val speed: String,
    val rated: Boolean,
    val fen: String,
    val lastMove: String?,
    val botToMove: Boolean,
    val botSecondsLeft: Int?,
    /** The monitor's own quiescence eval (not the bot's search score), centipawns from the bot's side. */
    val evalCp: Int? = null,
)

@Serializable
data class RecentGame(
    val id: String,
    val url: String,
    val colour: String,
    val opponent: String,
    val opponentRating: Int?,
    val speed: String,
    val rated: Boolean,
    val outcome: Outcome,
    val status: String,
    val ratingDiff: Int?,
    val endedAt: Long,
)

@Serializable
enum class Outcome { WIN, LOSS, DRAW, ABORTED }
