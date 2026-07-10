package com.tward.uci

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.game.ChessGame
import com.tward.engine.player.ChessBot
import com.tward.engine.player.ClockAware
import com.tward.engine.player.bot.MiniMaxIterativeDeepeningBot
import com.tward.logging.Log

/**
 * A minimal UCI (Universal Chess Interface) protocol handler that bridges a GUI or
 * Lichess client to one of this project's [ChessBot]s. It is transport-agnostic:
 * [handle] processes one input line and emits zero or more response lines through
 * [output], so it can be driven by stdin/stdout in production or by a list in tests.
 *
 * Only the subset of UCI needed to play games is implemented: uci, isready,
 * ucinewgame, position, go, stop and quit. The engine searches synchronously inside
 * `go`, so `stop` is a no-op (the bestmove has already been sent).
 *
 * No existing engine code is modified — the bridge reuses [Board.fromFEN],
 * [ChessGame] and the bot's `chooseMove(game, timeLeft)` contract directly.
 */
class UciEngine(
    private val output: (String) -> Unit,
    private val botFactory: (Colour) -> ChessBot = { colour ->
        MiniMaxIterativeDeepeningBot(colour = colour)
    }
) {

    private val log = Log.of<UciEngine>()

    private var game: ChessGame = ChessGame(Board.getStartingBoard())

    // Created lazily on the first `go` of a game so the bot's colour matches the side
    // we actually play, then reused for the rest of the game to preserve per-game state
    // (e.g. opening-book progress). Reset to null on `ucinewgame`.
    private var bot: ChessBot? = null

    /** Processes one UCI input line. Returns false when the engine should exit (quit). */
    fun handle(line: String): Boolean {
        val tokens = line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) {
            return true
        }

        try {
            when (tokens[0]) {
                "uci" -> handleUci()
                "isready" -> output("readyok")
                "ucinewgame" -> newGame()
                "position" -> handlePosition(tokens)
                "go" -> handleGo(tokens)
                "stop" -> { /* synchronous search: bestmove already sent */ }
                "quit" -> return false
                else -> log.debug { "Ignoring unsupported UCI command: $line" }
            }
        } catch (e: Exception) {
            log.warn { "Failed to handle UCI command '$line': ${e.message}" }
        }

        return true
    }

    private fun handleUci() {
        output("id name TwardEngine")
        output("id author tyler")
        output("uciok")
    }

    private fun newGame() {
        game = ChessGame(Board.getStartingBoard())
        bot = null
    }

    private fun handlePosition(tokens: List<String>) {
        var index = 1
        val board: Board

        when (tokens.getOrNull(index)) {
            "startpos" -> {
                board = Board.getStartingBoard()
                index++
            }

            "fen" -> {
                // A FEN is exactly six space-separated fields.
                val fen = tokens.subList(index + 1, index + 7).joinToString(" ")
                board = Board.fromFEN(fen)
                index += 7
            }

            else -> {
                log.warn { "Unrecognised position command: ${tokens.joinToString(" ")}" }
                return
            }
        }

        game = ChessGame(board)

        if (tokens.getOrNull(index) == "moves") {
            for (uci in tokens.subList(index + 1, tokens.size)) {
                val move = UciMoveCodec.findMove(game, uci)
                if (move == null) {
                    log.warn { "Illegal/unknown move '$uci' in position; stopping replay" }
                    break
                }
                game.makeMove(move)
            }
        }
    }

    private fun handleGo(tokens: List<String>) {
        val colour = game.board.activeColour
        val engineBot = bot ?: botFactory(colour).also { bot = it }

        if (game.getLegalMoves().isEmpty()) {
            output("bestmove 0000")
            return
        }

        val params = parseGoParams(tokens)
        val timeLeft = timeBudgetMillis(params, colour)

        (engineBot as? ClockAware)?.incrementMillis =
            (if (colour == Colour.WHITE) params["winc"] else params["binc"]) ?: 0

        val move = engineBot.chooseMove(game, timeLeft)
        output("bestmove ${UciMoveCodec.encode(move)}")
    }

    private fun timeBudgetMillis(params: Map<String, Int>, colour: Colour): Int {
        params["movetime"]?.let { return it }

        val clock = if (colour == Colour.WHITE) params["wtime"] else params["btime"]
        return clock ?: DEFAULT_TIME_MILLIS
    }

    private fun parseGoParams(tokens: List<String>): Map<String, Int> {
        val params = mutableMapOf<String, Int>()
        var i = 1
        while (i < tokens.size) {
            val key = tokens[i]
            if (key in TIMING_KEYS) {
                tokens.getOrNull(i + 1)?.toIntOrNull()?.let { params[key] = it }
                i += 2
            } else {
                i++
            }
        }
        return params
    }

    companion object {
        private const val DEFAULT_TIME_MILLIS = 10_000
        private val TIMING_KEYS = setOf("wtime", "btime", "winc", "binc", "movetime", "depth")
    }
}