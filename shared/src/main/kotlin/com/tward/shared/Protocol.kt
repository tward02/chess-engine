package com.tward.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire protocol shared between the server and its clients (web, mobile, desktop). Deliberately free
 * of any engine types — positions are FEN strings and moves are UCI long algebraic ("e2e4",
 * "e7e8q") so the contract stays portable to non-JVM clients. The server maps these to/from the
 * `:engine` model with `Board.fromFEN` / `UciMoveCodec`.
 */

enum class GameStatus { IN_PROGRESS, WHITE_WON, BLACK_WON, DRAW }

/** How a finished game ended; lets clients show "...by checkmate", "...on time", etc. */
enum class Termination {
    ONGOING,
    CHECKMATE,
    RESIGNATION,
    TIMEOUT,
    STALEMATE,
    THREEFOLD_REPETITION,
    FIFTY_MOVE_RULE,
    INSUFFICIENT_MATERIAL,
    AGREEMENT
}

/** A human-readable outcome, e.g. "White won by checkmate" or "Draw by threefold repetition". */
fun describeOutcome(status: GameStatus, termination: Termination): String {
    val who = when (status) {
        GameStatus.IN_PROGRESS -> return "In progress"
        GameStatus.WHITE_WON -> "White won"
        GameStatus.BLACK_WON -> "Black won"
        GameStatus.DRAW -> "Draw"
    }
    val how = when (termination) {
        Termination.CHECKMATE -> "by checkmate"
        Termination.RESIGNATION -> "by resignation"
        Termination.TIMEOUT -> "on time"
        Termination.STALEMATE -> "by stalemate"
        Termination.THREEFOLD_REPETITION -> "by threefold repetition"
        Termination.FIFTY_MOVE_RULE -> "by the fifty-move rule"
        Termination.INSUFFICIENT_MATERIAL -> "by insufficient material"
        Termination.AGREEMENT -> "by agreement"
        Termination.ONGOING -> ""
    }
    return if (how.isEmpty()) who else "$who $how"
}

/** A selectable opponent bot, as shown in the lobby. Full setup lives server-side (later: in a DB). */
@Serializable
data class BotInfo(
    val id: String,
    val name: String,
    val approxElo: Int,
    val description: String,
    val style: String              // a grouping tag, e.g. "Aggressive", "Positional", "Beginner"
)

/** Create a game against a catalog bot. Human-vs-human games are created via the lobby instead. */
@Serializable
data class CreateGameRequest(
    val botId: String,
    val playerColour: String = "white",        // colour the requesting human takes ("white"/"black")
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
    val termination: Termination = Termination.ONGOING,
    val legalMoves: List<String> = emptyList() // UCI strings for the side to move
) {
    /** A ready-to-display outcome string, e.g. "Black won on time". */
    fun outcomeText(): String = describeOutcome(status, termination)
}

// ---- In-game WebSocket (/ws/games/{id}) ----

/** Client -> server messages over the game WebSocket. */
@Serializable
sealed interface ClientMessage {
    @Serializable
    @SerialName("move")
    data class MakeMove(val uci: String) : ClientMessage
    @Serializable
    @SerialName("resign")
    data object Resign : ClientMessage
}

/** Server -> client messages over the game WebSocket. */
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

// ---- Lobby WebSocket (/ws/lobby) ----

enum class PlayerStatus { AVAILABLE, IN_GAME }

@Serializable
data class PlayerInfo(val id: String, val name: String, val status: PlayerStatus)

@Serializable
data class ChallengeInfo(
    val id: String,
    val fromId: String,
    val fromName: String,
    val toId: String,
    val challengerColour: String,              // colour the challenger will play
    val initialTimeMillis: Long,
    val incrementMillis: Long
)

/** Client -> server messages over the lobby WebSocket. */
@Serializable
sealed interface LobbyClientMessage {
    @Serializable
    @SerialName("join")
    data class Join(val name: String) : LobbyClientMessage

    @Serializable
    @SerialName("challengePlayer")
    data class ChallengePlayer(
        val opponentId: String,
        val colour: String = "white",
        val initialTimeMillis: Long = 300_000,
        val incrementMillis: Long = 0
    ) : LobbyClientMessage

    @Serializable
    @SerialName("challengeBot")
    data class ChallengeBot(
        val botId: String,
        val colour: String = "white",
        val initialTimeMillis: Long = 300_000,
        val incrementMillis: Long = 0
    ) : LobbyClientMessage

    @Serializable
    @SerialName("accept")
    data class AcceptChallenge(val challengeId: String) : LobbyClientMessage
    @Serializable
    @SerialName("decline")
    data class DeclineChallenge(val challengeId: String) : LobbyClientMessage
}

/** Server -> client messages over the lobby WebSocket. */
@Serializable
sealed interface LobbyServerMessage {
    @Serializable
    @SerialName("welcome")
    data class Welcome(val playerId: String) : LobbyServerMessage
    @Serializable
    @SerialName("players")
    data class Players(val players: List<PlayerInfo>) : LobbyServerMessage
    @Serializable
    @SerialName("bots")
    data class Bots(val bots: List<BotInfo>) : LobbyServerMessage
    @Serializable
    @SerialName("incomingChallenge")
    data class IncomingChallenge(val challenge: ChallengeInfo) : LobbyServerMessage
    @Serializable
    @SerialName("challengeDeclined")
    data class ChallengeDeclined(val challengeId: String) : LobbyServerMessage

    /** Sent to each participant when their game starts; [yourColour] is "white"/"black". */
    @Serializable
    @SerialName("gameStarted")
    data class GameStarted(val gameId: String, val yourColour: String) : LobbyServerMessage

    @Serializable
    @SerialName("lobbyError")
    data class LobbyError(val message: String) : LobbyServerMessage
}
