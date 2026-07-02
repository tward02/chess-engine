package com.tward.engine.nnue

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.Move
import com.tward.engine.game.ChessGame
import com.tward.engine.game.GameResult
import com.tward.engine.player.bot.AdvancedNegamaxBot
import com.tward.engine.tournament.winner
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.random.Random

/**
 * Generates NNUE training data by self-play. Each game opens with a few uniformly random plies (the
 * bots are deterministic, so this is what varies the games), then [AdvancedNegamaxBot] plays both
 * sides at a fixed depth. Every quiet position is recorded as a `FEN;score;result` line — the score
 * is the search's own root score in centipawns and the result 1/0.5/0, both from White's point of
 * view. Noisy positions (in check, a capture or promotion is best, or the score already decided) are
 * skipped: a static evaluator cannot resolve tactics and should not be trained on them — quiescence
 * handles those at play time.
 *
 * Run via `:engine:generateNnueData`, with `-PnnueArgs="games=... depth=... out=... threads=... seed=..."`.
 */
class TrainingDataGenerator(
    private val games: Int,
    private val searchDepth: Int,
    private val threads: Int,
    private val seed: Long,
    private val maxPlies: Int = 300
) {

    fun generate(out: Path) {
        out.toAbsolutePath().parent?.let(Files::createDirectories)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            Files.newBufferedWriter(out).use { writer ->
                val futures = (0 until games).map { index -> pool.submit<List<String>> { playAndRecord(index) } }
                val progress = ProgressReporter(games, "games")
                var positions = 0
                futures.forEachIndexed { index, future ->
                    val lines = future.get()
                    lines.forEach { writer.write(it); writer.newLine() }
                    positions += lines.size
                    progress.update(index + 1, ", $positions positions")
                }
                progress.finish("Wrote $positions positions from $games games to ${out.toAbsolutePath()}")
            }
        } finally {
            pool.shutdown()
        }
    }

    private fun playAndRecord(gameIndex: Int): List<String> {
        val random = Random(seed + gameIndex)
        val game = ChessGame(Board.getStartingBoard())

        repeat(OPENING_PLIES_MIN + random.nextInt(OPENING_PLIES_RANGE)) {
            if (game.getGameResult() != null) return@repeat
            val moves = game.getLegalMoves()
            if (moves.isEmpty()) return@repeat
            game.makeMove(moves[random.nextInt(moves.size)])
        }
        if (game.getGameResult() != null) return emptyList()   // the random opening ended the game

        val white = ScoringBot(Colour.WHITE, searchDepth)
        val black = ScoringBot(Colour.BLACK, searchDepth)
        val pending = mutableListOf<Pair<String, Int>>()

        var plies = 0
        var result: GameResult? = null
        while (plies < maxPlies) {
            result = game.getGameResult()
            if (result != null) break

            val whiteToMove = game.board.activeColour == Colour.WHITE
            val bot = if (whiteToMove) white else black
            val inCheck = game.isInCheck(game.board.activeColour)
            val fen = game.board.toFEN()

            val move = bot.chooseMove(game)

            val best = bot.lastBestMove
            val quiet = best != null && best.capturedPiece == null && best.promotionType == null
            if (quiet && !inCheck && abs(bot.lastScore) <= SCORE_CAP) {
                pending += fen to (if (whiteToMove) bot.lastScore else -bot.lastScore)
            }

            game.makeMove(move)
            plies++
        }

        val whitePoints = when (winner(result ?: GameResult.DRAW_50_MOVE_RULE)) {
            Colour.WHITE -> "1"
            Colour.BLACK -> "0"
            null -> "0.5"
        }
        return pending.map { (fen, score) -> "$fen;$score;$whitePoints" }
    }

    /** Exposes the root search score so each position is labelled by the search that chose its move. */
    private class ScoringBot(colour: Colour, depth: Int) : AdvancedNegamaxBot(
        colour = colour, fixedDepth = depth, useOpeningBookMoves = false, transpositionTableBits = 18
    ) {
        var lastScore = 0            // side-to-move perspective
            private set
        var lastBestMove: Move? = null
            private set

        override fun searchRoot(
            game: ChessGame, depth: Int, previousBest: Move?, alpha: Int, beta: Int
        ): Pair<Move, Int> {
            val result = super.searchRoot(game, depth, previousBest, alpha, beta)
            lastBestMove = result.first
            lastScore = result.second
            return result
        }
    }

    private companion object {
        const val OPENING_PLIES_MIN = 6
        const val OPENING_PLIES_RANGE = 7      // 6..12 random opening plies
        const val SCORE_CAP = 1_500            // beyond this the game is decided; nothing left to learn
    }
}

fun main(args: Array<String>) {
    val opts = args.associate { it.substringBefore('=') to it.substringAfter('=') }
    val games = opts["games"]?.toInt() ?: 2_000
    val depth = opts["depth"]?.toInt() ?: 4
    val threads = opts["threads"]?.toInt() ?: (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1)
    val seed = opts["seed"]?.toLong() ?: 20260702L
    val out = Paths.get(opts["out"] ?: "build/nnue/training-data.txt")

    println("Generating NNUE data: games=$games depth=$depth threads=$threads seed=$seed out=$out")
    TrainingDataGenerator(games, depth, threads, seed).generate(out)
}
