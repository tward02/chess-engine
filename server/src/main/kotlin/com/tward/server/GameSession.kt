package com.tward.server

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.Move
import com.tward.engine.game.ChessGame
import com.tward.engine.game.GameResult
import com.tward.engine.player.ChessBot
import com.tward.shared.*
import com.tward.uci.UciMoveCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/** Thrown for client errors (illegal move, not your turn, game over); mapped to HTTP 400 / WS error. */
class GameException(message: String) : RuntimeException(message)

/**
 * The authoritative state of one game — board, clocks, participants, and any bots. All mutation goes
 * through [submitMove]/[resign] under a [Mutex], so moves serialise across the REST caller, both
 * players' WebSockets and spectators. The server (not the client) validates every move against the
 * engine's legal moves, enforces whose turn it is, runs the clock, and decides the result. Each
 * change is published on [events] so live connections can be pushed the new state.
 *
 * Works for human-vs-bot and human-vs-human; either or both sides may be a bot.
 */
class GameSession(
    val id: String,
    private val white: Participant,
    private val black: Participant,
    initialTimeMillis: Long,
    private val incrementMillis: Long,
    scope: CoroutineScope
) {

    private val game = ChessGame(Board.getStartingBoard())
    private val mutex = Mutex()

    private val bots: Map<Colour, ChessBot> = buildMap {
        (white as? Participant.Bot)?.let { put(Colour.WHITE, BotFactory.build(it.spec, Colour.WHITE)) }
        (black as? Participant.Bot)?.let { put(Colour.BLACK, BotFactory.build(it.spec, Colour.BLACK)) }
    }

    private var whiteMillis = initialTimeMillis
    private var blackMillis = initialTimeMillis
    private var lastMove: Move? = null
    private var status = GameStatus.IN_PROGRESS
    private var termination = Termination.ONGOING
    private var turnStartNanos = System.nanoTime()

    private val _events = MutableSharedFlow<ServerMessage>(replay = 1, extraBufferCapacity = 16)
    val events: SharedFlow<ServerMessage> = _events

    init {
        _events.tryEmit(ServerMessage.State(toDto()))
        // A flagged clock is otherwise only noticed on the next move; this watcher ends a timed game
        // as soon as the side to move runs out of time, even if they never move again.
        if (initialTimeMillis > 0) scope.launch { watchClock() }
    }

    /** The colour a player controls, or null if they're not in this game. */
    fun colourOf(playerId: String): Colour? = when {
        (white as? Participant.Human)?.playerId == playerId -> Colour.WHITE
        (black as? Participant.Human)?.playerId == playerId -> Colour.BLACK
        else -> null
    }

    /** The single human's colour, when exactly one side is human (used by the REST bot-game endpoint). */
    fun soleHumanColour(): Colour? = when {
        white is Participant.Human && black is Participant.Bot -> Colour.WHITE
        black is Participant.Human && white is Participant.Bot -> Colour.BLACK
        else -> null
    }

    suspend fun state(): GameStateDto = mutex.withLock { toDto() }

    /** Drives any bot whose turn it is (e.g. a bot playing White moves first). Call once after creation. */
    suspend fun start(): GameStateDto = mutex.withLock {
        driveBots()
        toDto()
    }

    /** Applies [byColour]'s move (validated, turn-enforced), then lets bots reply. */
    suspend fun submitMove(uci: String, byColour: Colour): GameStateDto = mutex.withLock {
        if (status != GameStatus.IN_PROGRESS) throw GameException("Game is over")
        if (game.board.activeColour != byColour) throw GameException("It is not your turn")
        val move = UciMoveCodec.findMove(game, uci) ?: throw GameException("Illegal move: $uci")
        applyMove(move)
        driveBots()
        toDto()
    }

    suspend fun resign(byColour: Colour): GameStateDto = mutex.withLock {
        if (status == GameStatus.IN_PROGRESS) {
            status = if (byColour == Colour.WHITE) GameStatus.BLACK_WON else GameStatus.WHITE_WON
            termination = Termination.RESIGNATION
            _events.tryEmit(ServerMessage.State(toDto()))
            _events.tryEmit(ServerMessage.GameOver(status, describeOutcome(status, termination)))
        }
        toDto()
    }

    private suspend fun driveBots() {
        while (status == GameStatus.IN_PROGRESS) {
            val bot = bots[game.board.activeColour] ?: break
            val timeLeft = clockOf(game.board.activeColour).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            // Search on a copy so a search bug can never corrupt the live game, then re-resolve.
            val chosen = withContext(Dispatchers.Default) { bot.chooseMove(game.copy(), timeLeft) }
            val resolved = UciMoveCodec.findMove(game, UciMoveCodec.encode(chosen)) ?: break
            applyMove(resolved)
        }
    }

    private fun applyMove(move: Move) {
        val mover = game.board.activeColour
        game.makeMove(move)
        lastMove = move
        chargeClock(mover)
        if (status == GameStatus.IN_PROGRESS) {
            status = boardStatus()
            if (status != GameStatus.IN_PROGRESS) termination = terminationOf(game.getGameResult())
        }
        turnStartNanos = System.nanoTime()

        _events.tryEmit(ServerMessage.State(toDto()))
        if (status != GameStatus.IN_PROGRESS) {
            _events.tryEmit(ServerMessage.GameOver(status, describeOutcome(status, termination)))
        }
    }

    private fun chargeClock(mover: Colour) {
        val elapsedMs = (System.nanoTime() - turnStartNanos) / 1_000_000
        if (mover == Colour.WHITE) {
            whiteMillis = whiteMillis - elapsedMs + incrementMillis
            if (whiteMillis <= 0) {
                whiteMillis = 0; status = GameStatus.BLACK_WON; termination = Termination.TIMEOUT
            }
        } else {
            blackMillis = blackMillis - elapsedMs + incrementMillis
            if (blackMillis <= 0) {
                blackMillis = 0; status = GameStatus.WHITE_WON; termination = Termination.TIMEOUT
            }
        }
    }

    private fun terminationOf(result: GameResult?): Termination = when (result) {
        GameResult.WHITE_WIN, GameResult.BLACK_WIN -> Termination.CHECKMATE
        GameResult.WHITE_TIME_WIN, GameResult.BLACK_TIME_WIN -> Termination.TIMEOUT
        GameResult.WHITE_RESIGNATION, GameResult.BLACK_RESIGNATION -> Termination.RESIGNATION
        GameResult.DRAW_STALEMATE -> Termination.STALEMATE
        GameResult.DRAW_THREEFOLD_REPETITION -> Termination.THREEFOLD_REPETITION
        GameResult.DRAW_50_MOVE_RULE -> Termination.FIFTY_MOVE_RULE
        GameResult.DRAW_INSUFFICIENT_MATERIAL -> Termination.INSUFFICIENT_MATERIAL
        GameResult.DRAW_AGREED -> Termination.AGREEMENT
        null -> Termination.ONGOING
    }

    private fun boardStatus(): GameStatus = when (val result = game.getGameResult()) {
        null -> GameStatus.IN_PROGRESS
        GameResult.WHITE_WIN, GameResult.WHITE_TIME_WIN, GameResult.BLACK_RESIGNATION -> GameStatus.WHITE_WON
        GameResult.BLACK_WIN, GameResult.BLACK_TIME_WIN, GameResult.WHITE_RESIGNATION -> GameStatus.BLACK_WON
        else -> if (result.isDraw()) GameStatus.DRAW else GameStatus.IN_PROGRESS
    }

    private fun clockOf(colour: Colour): Long = if (colour == Colour.WHITE) whiteMillis else blackMillis

    // Polls the side-to-move's live clock and ends the game the moment it hits zero. Runs until the
    // game is over (by timeout or otherwise), then stops.
    private suspend fun watchClock() {
        while (true) {
            delay(CLOCK_TICK_MILLIS.milliseconds)
            val finished = mutex.withLock {
                if (status == GameStatus.IN_PROGRESS) flagIfTimedOut()
                status != GameStatus.IN_PROGRESS
            }
            if (finished) break
        }
    }

    private fun flagIfTimedOut() {
        val active = game.board.activeColour
        val remaining = clockOf(active) - (System.nanoTime() - turnStartNanos) / 1_000_000
        if (remaining > 0) return

        if (active == Colour.WHITE) {
            whiteMillis = 0; status = GameStatus.BLACK_WON
        } else {
            blackMillis = 0; status = GameStatus.WHITE_WON
        }
        termination = Termination.TIMEOUT
        _events.tryEmit(ServerMessage.State(toDto()))
        _events.tryEmit(ServerMessage.GameOver(status, describeOutcome(status, termination)))
    }

    private fun toDto(): GameStateDto = GameStateDto(
        gameId = id,
        fen = game.board.toFEN(),
        sideToMove = if (game.board.activeColour == Colour.WHITE) "white" else "black",
        lastMove = lastMove?.let { UciMoveCodec.encode(it) },
        whiteMillis = whiteMillis,
        blackMillis = blackMillis,
        status = status,
        termination = termination,
        legalMoves = if (status == GameStatus.IN_PROGRESS) {
            game.getLegalMoves().map { UciMoveCodec.encode(it) }
        } else {
            emptyList()
        }
    )

    private companion object {
        const val CLOCK_TICK_MILLIS = 200L
    }
}
