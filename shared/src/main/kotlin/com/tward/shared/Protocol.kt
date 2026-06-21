package com.tward.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire protocol shared between the server and its clients (web, mobile, desktop). Deliberately free
 * of any engine types — positions are FEN strings and moves are UCI long algebraic ("e2e4",
 * "e7e8q") so the contract stays portable to non-JVM clients. The server maps these to/from the
 * `:engine` model with `Board.fromFEN` / `UciMoveCodec`.
 */

enum class PlayerKind { HUMAN, BOT }

/** Bot strength tier; the server maps each to a concrete bot + search budget. */
enum class BotDifficulty { EASY, MEDIUM, HARD }

enum class GameStatus { IN_PROGRESS, WHITE_WON, BLACK_WON, DRAW }

@Serializable
data class CreateGameRequest(
    val opponent: PlayerKind = PlayerKind.BOT,
    val difficulty: BotDifficulty = BotDifficulty.HARD,
    val playerColour: String = "white",       // colour the requesting human takes ("white"/"black")
    val initialTimeMillis: Long = 300_000,
    val incrementMillis: Long = 0
)

/** Body of `POST /api/games/{id}/moves`. */
@Serializable
data class MoveRequest(val uci: String)

/** Standard error envelope returned by the REST API. */
@Serializable
data class ErrorResponse(val error: String)

/** A snapshot of a game, enough for a client to render the board, clocks and legal moves. */
@Serializable
data class GameStateDto(
    val gameId: String,
    val fen: String,
    val sideToMove: String,                    // "white" / "black"
    val lastMove: String? = null,              // UCI, or null before the first move
    val whiteMillis: Long,
    val blackMillis: Long,
    val status: GameStatus,
    val legalMoves: List<String> = emptyList() // UCI strings for the side to move
)

/** Client -> server messages over the WebSocket. */
@Serializable
sealed interface ClientMessage {
    @Serializable
    @SerialName("move")
    data class MakeMove(val uci: String) : ClientMessage

    @Serializable
    @SerialName("resign")
    data object Resign : ClientMessage
}

/** Server -> client messages over the WebSocket. */
@Serializable
sealed interface ServerMessage {
    @Serializable
    @SerialName("state")
    data class State(val game: GameStateDto) : ServerMessage

    @Serializable
    @SerialName("gameOver")
    data class GameOver(val status: GameStatus, val reason: String) : ServerMessage

    @Serializable
    @SerialName("error")
    data class Error(val message: String) : ServerMessage
}
