package com.tward.monitor

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class Profile(val ratings: Map<String, PerfRating>)
data class Presence(val online: Boolean, val playing: Boolean)

/** The Lichess reads the poller needs; an interface so tests can fake it. */
interface LichessSource {
    suspend fun profile(): Profile
    suspend fun presence(): Presence
    suspend fun recentGames(max: Int): List<RecentGame>

    /** Null when no token is configured. */
    suspend fun playing(): List<LiveGame>?
}

/** Lichess answered 429: its docs ask clients to wait a full minute before retrying. */
class RateLimitedException : RuntimeException("Lichess rate limit hit (429)")

class LichessHttpSource(private val config: MonitorConfig) : LichessSource, AutoCloseable {

    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
        }
        expectSuccess = false
    }

    override suspend fun profile(): Profile =
        LichessParsing.profile(text(client.get("${config.lichessUrl}/api/user/${config.bot}")))

    override suspend fun presence(): Presence =
        LichessParsing.presence(text(client.get("${config.lichessUrl}/api/users/status") {
            parameter("ids", config.botId)
        }))

    override suspend fun recentGames(max: Int): List<RecentGame> =
        LichessParsing.recentGames(
            text(client.get("${config.lichessUrl}/api/games/user/${config.bot}") {
                parameter("max", max)
                parameter("moves", false)
                header(HttpHeaders.Accept, "application/x-ndjson")
            }),
            config.botId, config.lichessUrl,
        )

    override suspend fun playing(): List<LiveGame>? {
        val token = config.token ?: return null
        return LichessParsing.playing(
            text(client.get("${config.lichessUrl}/api/account/playing") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }),
            config.lichessUrl,
        )
    }

    private suspend fun text(response: HttpResponse): String {
        if (response.status == HttpStatusCode.TooManyRequests) throw RateLimitedException()
        check(response.status.isSuccess()) { "${response.call.request.url.encodedPath} returned ${response.status}" }
        return response.bodyAsText()
    }

    override fun close() = client.close()
}

/**
 * Pure parsers for the Lichess responses. They navigate the JSON loosely rather than binding to
 * fixed DTOs, so an added or missing field on Lichess's side degrades a value instead of failing.
 */
object LichessParsing {

    private val json = Json { ignoreUnknownKeys = true }

    /** The speeds shown as ratings, in display order. */
    val SPEEDS = listOf("bullet", "blitz", "rapid", "classical")

    fun profile(body: String): Profile {
        val perfs = json.parseToJsonElement(body).obj["perfs"]?.obj ?: return Profile(emptyMap())
        val ratings = SPEEDS.mapNotNull { speed ->
            val perf = perfs[speed]?.obj ?: return@mapNotNull null
            val rating = perf.int("rating") ?: return@mapNotNull null
            speed to PerfRating(
                rating = rating,
                games = perf.int("games") ?: 0,
                provisional = perf.bool("prov") ?: false,
                progress = perf.int("prog") ?: 0,
            )
        }.toMap()
        return Profile(ratings)
    }

    fun presence(body: String): Presence {
        val user = (json.parseToJsonElement(body) as? JsonArray)?.firstOrNull()?.obj
            ?: return Presence(online = false, playing = false)
        return Presence(online = user.bool("online") ?: false, playing = user.bool("playing") ?: false)
    }

    fun recentGames(ndjson: String, botId: String, baseUrl: String): List<RecentGame> =
        ndjson.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line -> runCatching { recentGame(json.parseToJsonElement(line).obj, botId, baseUrl) }.getOrNull() }
            .toList()

    private fun recentGame(game: JsonObject, botId: String, baseUrl: String): RecentGame? {
        val id = game.str("id") ?: return null
        val players = game["players"]?.obj ?: return null
        val whiteId = players["white"]?.obj?.get("user")?.obj?.str("id")
        val colour = if (whiteId.equals(botId, ignoreCase = true)) "white" else "black"
        val bot = players[colour]?.obj
        val opponent = players[if (colour == "white") "black" else "white"]?.obj
        val status = game.str("status") ?: "unknown"
        val winner = game.str("winner")
        val outcome = when {
            status == "aborted" || status == "noStart" -> Outcome.ABORTED
            winner == null -> Outcome.DRAW
            winner == colour -> Outcome.WIN
            else -> Outcome.LOSS
        }
        return RecentGame(
            id = id,
            url = "$baseUrl/$id",
            colour = colour,
            opponent = opponent?.let(::playerName) ?: "?",
            opponentRating = opponent?.int("rating"),
            speed = game.str("speed") ?: game.str("perf") ?: "?",
            rated = game.bool("rated") ?: false,
            outcome = outcome,
            status = status,
            ratingDiff = bot?.int("ratingDiff"),
            endedAt = game.long("lastMoveAt") ?: game.long("createdAt") ?: 0,
        )
    }

    private fun playerName(player: JsonObject): String {
        player["user"]?.obj?.str("name")?.let { return it }
        player.int("aiLevel")?.let { return "Stockfish level $it" }
        return "Anonymous"
    }

    fun playing(body: String, baseUrl: String): List<LiveGame> {
        val games = json.parseToJsonElement(body).obj["nowPlaying"] as? JsonArray ?: return emptyList()
        return games.mapNotNull { element ->
            val game = element.obj
            val id = game.str("gameId") ?: return@mapNotNull null
            val colour = game.str("color") ?: "white"
            val botToMove = game.bool("isMyTurn") ?: false
            val opponent = game["opponent"]?.obj
            val variant = game["variant"]?.obj?.str("key") ?: "standard"
            if (variant != "standard") return@mapNotNull null
            val sideToMove = if (botToMove == (colour == "white")) "w" else "b"
            LiveGame(
                id = id,
                url = "$baseUrl/$id",
                colour = colour,
                opponent = opponent?.str("username") ?: opponent?.int("ai")?.let { "Stockfish level $it" } ?: "?",
                opponentRating = opponent?.int("rating"),
                speed = game.str("speed") ?: "?",
                rated = game.bool("rated") ?: false,
                fen = normaliseFen(game.str("fen") ?: return@mapNotNull null, sideToMove),
                lastMove = game.str("lastMove")?.takeIf { it.isNotEmpty() },
                botToMove = botToMove,
                botSecondsLeft = game.int("secondsLeft"),
            )
        }
    }

    /**
     * Pads a placement-only FEN (which `/api/account/playing` may return) to the full six fields
     * `Board.fromFEN` requires. Castling and en passant are unknown in that case, which is fine
     * for the static eval it feeds.
     */
    fun normaliseFen(fen: String, sideToMove: String): String {
        val parts = fen.trim().split(Regex("\\s+")).toMutableList()
        val defaults = listOf(sideToMove, "-", "-", "0", "1")
        while (parts.size < 6) parts += defaults[parts.size - 1]
        return parts.take(6).joinToString(" ")
    }

    private val JsonElement.obj: JsonObject get() = jsonObject
    private fun JsonObject.str(key: String): String? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.int(key: String): Int? = runCatching { this[key]?.jsonPrimitive?.intOrNull }.getOrNull()
    private fun JsonObject.long(key: String): Long? = runCatching { this[key]?.jsonPrimitive?.longOrNull }.getOrNull()
    private fun JsonObject.bool(key: String): Boolean? = runCatching { this[key]?.jsonPrimitive?.booleanOrNull }.getOrNull()
}
