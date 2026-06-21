package com.tward.server

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.Move
import com.tward.engine.game.ChessGame
import com.tward.engine.game.GameResult
import com.tward.engine.player.ChessBot
import com.tward.shared.CreateGameRequest
import com.tward.shared.GameStateDto
import com.tward.shared.GameStatus
import com.tward.shared.PlayerKind
import com.tward.shared.ServerMessage
import com.tward.uci.UciMoveCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Thrown for client errors (illegal move, game already over); mapped to HTTP 400 by the routing. */
class GameException(message: String) : RuntimeException(message)

/**
 * The authoritative state of one game: the board, the clocks, and (for a bot game) the bot. All
 * mutation goes through [submitMove]/[resign] under a [Mutex], so moves serialise even with a REST
 * caller and several WebSocket subscribers. Every change is published on [events] so live clients
 * (and spectators) can be pushed the new state. The server — never the client — validates moves
 * (against the engine's legal moves), runs the clock, and decides the result.
 */
class GameSession(
    val id: String,
    private val request: CreateGameRequest,
    engineService: EngineService
) {

    private val game = ChessGame(Board.getStartingBoard())
    private val mutex = Mutex()

    private val humanColour = if (request.playerColour.lowercase() == "black") Colour.BLACK else Colour.WHITE
    private val botColour: Colour? = if (request.opponent == PlayerKind.BOT) humanColour.opposite() else null
    private val bot: ChessBot? = botColour?.let { engineService.createBot(request.difficulty, it) }

    private var whiteMillis = request.initialTimeMillis
    private var blackMillis = request.initialTimeMillis
    private var lastMove: Move? = null
    private var status = GameStatus.IN_PROGRESS
    private var turnStartNanos = System.nanoTime()

    private val _events = MutableSharedFlow<ServerMessage>(replay = 1, extraBufferCapacity = 16)
    val events: SharedFlow<ServerMessage> = _events

    init {
        _events.tryEmit(ServerMessage.State(toDto()))
    }

    suspend fun state(): GameStateDto = mutex.withLock { toDto() }

    /** Applies a validated human move, then lets the bot reply while it is the bot's turn. */
    suspend fun submitMove(uci: String): GameStateDto = mutex.withLock {
        if (status != GameStatus.IN_PROGRESS) throw GameException("Game is over")
        val move = UciMoveCodec.findMove(game, uci) ?: throw GameException("Illegal move: $uci")
        applyMove(move)

        while (status == GameStatus.IN_PROGRESS && bot != null && game.board.activeColour == botColour) {
            val timeLeft = clockOf(botColour).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            // Search on a copy so a search bug can never corrupt the live game, then re-resolve.
            val chosen = withContext(Dispatchers.Default) { bot.chooseMove(game.copy(), timeLeft) }
            val resolved = UciMoveCodec.findMove(game, UciMoveCodec.encode(chosen)) ?: break
            applyMove(resolved)
        }
        toDto()
    }

    /** Resigns the side currently to move. (Identity/auth comes in a later step.) */
    suspend fun resign(): GameStateDto = mutex.withLock {
        if (status == GameStatus.IN_PROGRESS) {
            status = if (game.board.activeColour == Colour.WHITE) GameStatus.BLACK_WON else GameStatus.WHITE_WON
            _events.tryEmit(ServerMessage.State(toDto()))
            _events.tryEmit(ServerMessage.GameOver(status, "resignation"))
        }
        toDto()
    }

    private fun applyMove(move: Move) {
        val mover = game.board.activeColour
        game.makeMove(move)
        lastMove = move
        chargeClock(mover)
        if (status == GameStatus.IN_PROGRESS) status = boardStatus()
        turnStartNanos = System.nanoTime()

        _events.tryEmit(ServerMessage.State(toDto()))
        if (status != GameStatus.IN_PROGRESS) {
            _events.tryEmit(ServerMessage.GameOver(status, game.getGameResult()?.toString() ?: "game over"))
        }
    }

    private fun chargeClock(mover: Colour) {
        val elapsedMs = (System.nanoTime() - turnStartNanos) / 1_000_000
        if (mover == Colour.WHITE) {
            whiteMillis = whiteMillis - elapsedMs + request.incrementMillis
            if (whiteMillis <= 0) { whiteMillis = 0; status = GameStatus.BLACK_WON }
        } else {
            blackMillis = blackMillis - elapsedMs + request.incrementMillis
            if (blackMillis <= 0) { blackMillis = 0; status = GameStatus.WHITE_WON }
        }
    }

    private fun boardStatus(): GameStatus = when (val result = game.getGameResult()) {
        null -> GameStatus.IN_PROGRESS
        GameResult.WHITE_WIN, GameResult.WHITE_TIME_WIN, GameResult.BLACK_RESIGNATION -> GameStatus.WHITE_WON
        GameResult.BLACK_WIN, GameResult.BLACK_TIME_WIN, GameResult.WHITE_RESIGNATION -> GameStatus.BLACK_WON
        else -> if (result.isDraw()) GameStatus.DRAW else GameStatus.IN_PROGRESS
    }

    private fun clockOf(colour: Colour): Long = if (colour == Colour.WHITE) whiteMillis else blackMillis

    private fun toDto(): GameStateDto = GameStateDto(
        gameId = id,
        fen = game.board.toFEN(),
        sideToMove = if (game.board.activeColour == Colour.WHITE) "white" else "black",
        lastMove = lastMove?.let { UciMoveCodec.encode(it) },
        whiteMillis = whiteMillis,
        blackMillis = blackMillis,
        status = status,
        legalMoves = if (status == GameStatus.IN_PROGRESS) {
            game.getLegalMoves().map { UciMoveCodec.encode(it) }
        } else {
            emptyList()
        }
    )
}
