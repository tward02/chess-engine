package com.tward.uci

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.game.ChessGame
import com.tward.engine.player.ChessBot
import com.tward.engine.player.ClockAware
import com.tward.engine.player.Ponderable
import com.tward.engine.player.bot.MiniMaxIterativeDeepeningBot
import com.tward.logging.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A minimal UCI (Universal Chess Interface) protocol handler that bridges a GUI or
 * Lichess client to one of this project's [ChessBot]s. It is transport-agnostic:
 * [handle] processes one input line and emits zero or more response lines through
 * [output], so it can be driven by stdin/stdout in production or by a list in tests.
 *
 * Only the subset of UCI needed to play games is implemented: uci, isready,
 * ucinewgame, position, go, stop, ponderhit and quit.
 *
 * By default the engine searches synchronously inside `go` (convenient for tests). With
 * [asyncSearch] each search runs on a background thread so the input loop stays responsive,
 * which enables pondering for [Ponderable] bots: `bestmove` names the reply the bot expects, and
 * `go ponder` searches that position on the opponent's time until `stop` (wrong guess, or the
 * driver simply restarting — python-chess always does this) or `ponderhit` (searched for real,
 * using the `go ponder` clocks). Either way the bot's transposition table is warm for the next move.
 *
 * No existing engine code is modified — the bridge reuses [Board.fromFEN],
 * [ChessGame] and the bot's `chooseMove(game, timeLeft)` contract directly.
 */
class UciEngine(
    private val output: (String) -> Unit,
    private val botFactory: (Colour) -> ChessBot = { colour ->
        MiniMaxIterativeDeepeningBot(colour = colour)
    },
    private val asyncSearch: Boolean = false
) {

    private val log = Log.of<UciEngine>()

    private var game: ChessGame = ChessGame(Board.getStartingBoard())

    // Created lazily on the first `go` of a game so the bot's colour matches the side
    // we actually play, then reused for the rest of the game to preserve per-game state
    // (e.g. opening-book progress). Reset to null on `ucinewgame`.
    private var bot: ChessBot? = null

    private var searchThread: Thread? = null
    private var activePonder: PonderSignal? = null

    private class PonderSignal {
        val stop = AtomicBoolean(false)
        val released = CountDownLatch(1)
        @Volatile var hit = false

        fun release(hit: Boolean) {
            this.hit = hit
            stop.set(true)
            released.countDown()
        }
    }

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
                "ucinewgame" -> { awaitSearch(); newGame() }
                "position" -> { awaitSearch(); handlePosition(tokens) }
                "go" -> { awaitSearch(); handleGo(tokens) }
                // A normal search ends on its own budget; only pondering waits to be told.
                "stop" -> activePonder?.release(hit = false)
                "ponderhit" -> activePonder?.release(hit = true)
                "quit" -> { activePonder?.release(hit = false); return false }
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
        if (asyncSearch) output("option name Ponder type check default false")
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

    /** Blocks until the current search (if any) has sent its bestmove; a ponder is stopped first. */
    fun awaitSearch() {
        activePonder?.release(hit = false)
        searchThread?.join()
        searchThread = null
        activePonder = null
    }

    private fun handleGo(tokens: List<String>) {
        // A ponder position is the one after the expected reply, so it is our side to move too.
        val colour = game.board.activeColour
        val engineBot = bot ?: botFactory(colour).also { bot = it }
        val searchGame = game
        val params = parseGoParams(tokens)

        if (searchGame.getLegalMoves().isEmpty()) {
            output("bestmove 0000")
            return
        }

        if (!asyncSearch) {
            search(engineBot, searchGame, params)
            return
        }

        val ponderer = engineBot as? Ponderable
        if ("ponder" in tokens && ponderer != null) {
            val signal = PonderSignal()
            activePonder = signal
            searchThread = startThread {
                ponderer.ponder(searchGame) { signal.stop.get() }
                signal.released.await()
                if (signal.hit) {
                    search(engineBot, searchGame, params)
                } else {
                    // The driver discards a stopped ponder's move, but UCI still requires one.
                    output("bestmove ${UciMoveCodec.encode(searchGame.getLegalMoves().first())}")
                }
            }
        } else {
            searchThread = startThread { search(engineBot, searchGame, params) }
        }
    }

    private fun search(engineBot: ChessBot, searchGame: ChessGame, params: Map<String, Int>) {
        val colour = searchGame.board.activeColour
        (engineBot as? ClockAware)?.incrementMillis =
            (if (colour == Colour.WHITE) params["winc"] else params["binc"]) ?: 0

        val move = try {
            engineBot.chooseMove(searchGame, timeBudgetMillis(params, colour))
        } catch (e: Exception) {
            // Any legal move beats silence: without a bestmove the driver waits until we flag.
            log.error(e) { "Search failed, playing the first legal move: ${e.message}" }
            searchGame.getLegalMoves().first()
        }
        val reply = if (asyncSearch) (engineBot as? Ponderable)?.expectedReply(searchGame, move) else null
        output(
            if (reply != null) "bestmove ${UciMoveCodec.encode(move)} ponder ${UciMoveCodec.encode(reply)}"
            else "bestmove ${UciMoveCodec.encode(move)}"
        )
    }

    private fun startThread(body: () -> Unit): Thread =
        Thread({
            try {
                body()
            } catch (e: Exception) {
                log.error(e) { "Search failed: ${e.message}" }
            }
        }, "uci-search").apply {
            isDaemon = true   // quit must not wait for a search
            start()
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
