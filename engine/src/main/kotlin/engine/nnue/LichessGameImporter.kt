package com.tward.engine.nnue

import com.tward.engine.board.Board
import com.tward.engine.game.ChessGame
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Turns the bot's real Lichess games into NNUE training data: positions it actually reaches,
 * weighted towards the games it lost. Each sampled position is re-scored by a [PositionLabeler]
 * search (the game's own moves were played under a clock, so they're not labels) and paired with
 * the real game result. Output is the same `FEN;score;result` format as [TrainingDataGenerator], so
 * the trainer takes it alongside self-play files.
 *
 * Games are downloaded once to an NDJSON cache (public data; `token=` or `LICHESS_TOKEN` only raises
 * the rate limit), then re-labelled from the cache. Run via `:engine:importLichessGames`, with
 * `-PnnueArgs="user=tward-bot max=2000 bot=omega nodes=20000 lossEvery=1 otherEvery=3 ..."`.
 */
class LichessGameImporter(
    private val user: String,
    private val label: LabelConfig,
    private val threads: Int,
    /** Positions before this ply are skipped: the opening is the book's job. */
    private val minPly: Int = 10,
    /** Sample every Nth position from games the bot lost / from the rest. */
    private val lossEvery: Int = 1,
    private val otherEvery: Int = 3
) {

    fun download(cache: Path, max: Int, perfTypes: String, token: String?) {
        cache.toAbsolutePath().parent?.let(Files::createDirectories)
        val query = "max=$max&perfType=${encode(perfTypes)}&moves=true&tags=false&clocks=false" +
                "&evals=false&opening=false&pgnInJson=false"
        val request = HttpRequest.newBuilder(URI("https://lichess.org/api/games/user/${encode(user)}?$query"))
            .header("Accept", "application/x-ndjson")
            .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
            .GET()
            .build()
        println("Downloading up to $max $perfTypes games of $user ...")
        // Downloaded beside the cache and moved in only on success, so an error page never becomes the cache.
        val partial = cache.resolveSibling("${cache.fileName}.part")
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofFile(partial))
        if (response.statusCode() != 200) {
            Files.deleteIfExists(partial)
            error(
                "Lichess answered HTTP ${response.statusCode()}" +
                        (if (response.statusCode() == 429) " (rate limited: wait a minute, or pass token=)" else "")
            )
        }
        Files.move(partial, cache, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        println("Saved ${Files.lines(cache).use { it.count() }} games to ${cache.toAbsolutePath()}")
    }

    fun label(cache: Path, out: Path) {
        val games = Files.readAllLines(cache).filter { it.isNotBlank() }.mapNotNull(::parseGame)
        val lost = games.count { it.botLost }
        println("Labelling ${games.size} standard games ($lost lost) with ${label.describe()}, $threads threads")

        val seen = ConcurrentHashMap.newKeySet<Long>()
        val labelers = ThreadLocal.withInitial { PositionLabeler(label) }
        val skippedGames = AtomicInteger()
        val pool = Executors.newFixedThreadPool(threads)
        out.toAbsolutePath().parent?.let(Files::createDirectories)
        try {
            Files.newBufferedWriter(out).use { writer ->
                val futures = games.map { game ->
                    pool.submit<List<String>> { labelGame(game, labelers.get(), seen, skippedGames) }
                }
                val progress = ProgressReporter(games.size, "games")
                var positions = 0
                futures.forEachIndexed { index, future ->
                    val lines = future.get()
                    lines.forEach { writer.write(it); writer.newLine() }
                    positions += lines.size
                    progress.update(index + 1, ", $positions positions")
                }
                progress.finish(
                    "Wrote $positions positions to ${out.toAbsolutePath()}" +
                            (if (skippedGames.get() > 0) " (${skippedGames.get()} games had unreadable moves)" else "")
                )
            }
        } finally {
            pool.shutdown()
        }
    }

    private class ImportedGame(val sanMoves: List<String>, val whitePoints: String, val botLost: Boolean)

    private fun parseGame(line: String): ImportedGame? {
        val obj = Json.parseToJsonElement(line).jsonObject
        if (obj.string("variant") != "standard") return null
        val moves = obj.string("moves")?.split(' ')?.filter { it.isNotEmpty() } ?: return null
        if (moves.size <= minPly) return null

        val botIsWhite = obj.player("white")?.equals(user, ignoreCase = true) == true
        val winner = obj.string("winner")
        val whitePoints = when (winner) {
            "white" -> "1"
            "black" -> "0"
            else -> "0.5"
        }
        val botLost = winner != null && (winner == "white") != botIsWhite
        return ImportedGame(moves, whitePoints, botLost)
    }

    private fun labelGame(
        imported: ImportedGame,
        labeler: PositionLabeler,
        seen: MutableSet<Long>,
        skippedGames: AtomicInteger
    ): List<String> {
        val game = ChessGame(Board.getStartingBoard())
        val stride = if (imported.botLost) lossEvery else otherEvery
        val lines = mutableListOf<String>()
        for ((ply, san) in imported.sanMoves.withIndex()) {
            if (game.getGameResult() != null) break
            if (ply >= minPly && (ply - minPly) % stride == 0 && seen.add(game.board.zobristKey)) {
                val fen = game.board.toFEN()
                labeler.label(game).whiteScore?.let { lines += "$fen;$it;${imported.whitePoints}" }
            }
            val move = SanParser.findMove(game, san)
            if (move == null) {
                skippedGames.incrementAndGet()
                break
            }
            game.makeMove(move)
        }
        return lines
    }

    private fun JsonObject.string(key: String): String? = this[key]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }

    private fun JsonObject.player(colour: String): String? = runCatching {
        val player = this["players"]!!.jsonObject[colour]!!.jsonObject["user"]!!.jsonObject
        (player["id"] ?: player["name"])!!.jsonPrimitive.content
    }.getOrNull()

    private fun encode(s: String) = URLEncoder.encode(s, StandardCharsets.UTF_8)
}

fun main(args: Array<String>) {
    val opts = args.associate { it.substringBefore('=') to it.substringAfter('=') }
    val user = opts["user"] ?: "tward-bot"
    // The deployed bot is the default teacher here: these are exactly the positions it plays.
    val label = LabelConfig.fromOptions(mapOf("bot" to "omega", "nodes" to "20000") + opts)
    val threads = opts["threads"]?.toInt() ?: (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1)
    val cache = Paths.get(opts["games"] ?: "../nnue-data/lichess-$user.ndjson")
    val out = Paths.get(opts["out"] ?: "../nnue-data/lichess-$user.txt")

    val importer = LichessGameImporter(
        user, label, threads,
        minPly = opts["minPly"]?.toInt() ?: 10,
        lossEvery = opts["lossEvery"]?.toInt() ?: 1,
        otherEvery = opts["otherEvery"]?.toInt() ?: 3
    )
    if (!Files.exists(cache) || opts["refresh"] == "true") {
        importer.download(
            cache,
            max = opts["max"]?.toInt() ?: 2_000,
            perfTypes = opts["perf"] ?: "bullet,blitz,rapid",
            token = opts["token"] ?: System.getenv("LICHESS_TOKEN")
        )
    } else {
        println("Using cached games in ${cache.toAbsolutePath()} (refresh=true to re-download)")
    }
    importer.label(cache, out)
}
