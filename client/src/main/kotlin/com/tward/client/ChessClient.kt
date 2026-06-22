package com.tward.client

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tward.engine.board.Square
import com.tward.shared.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

enum class Screen { CONNECT, LOBBY, GAME }

/**
 * Drives the lightweight test client: holds Compose-observable UI state and talks to the server over
 * the lobby and game WebSockets. WebSocket reads run on [scope] (the Compose UI dispatcher); ktor's
 * frames are suspending, so the UI thread is never blocked and state is mutated on it directly.
 */
class ChessClient(private val scope: CoroutineScope) {

    private val json = Json { classDiscriminator = "type"; ignoreUnknownKeys = true; encodeDefaults = true }
    private val http = HttpClient(CIO) { install(WebSockets) }

    var screen by mutableStateOf(Screen.CONNECT)
    var status by mutableStateOf("Not connected")
    var myId: String? = null

    var players by mutableStateOf<List<PlayerInfo>>(emptyList())
    var bots by mutableStateOf<List<BotInfo>>(emptyList())
    var challenges by mutableStateOf<List<ChallengeInfo>>(emptyList())

    var game by mutableStateOf<GameStateDto?>(null)
    var myColour by mutableStateOf("white")
    var selected by mutableStateOf<Square?>(null)

    private var baseWs = "ws://localhost:8080"
    private var lobby: DefaultClientWebSocketSession? = null
    private var gameSocket: DefaultClientWebSocketSession? = null

    fun connect(host: String, port: Int, name: String) {
        baseWs = "ws://$host:$port"
        status = "Connecting…"
        scope.launch {
            try {
                http.webSocket("$baseWs/ws/lobby") {
                    lobby = this
                    send(Frame.Text(json.encodeToString<LobbyClientMessage>(LobbyClientMessage.Join(name))))
                    for (frame in incoming) {
                        if (frame is Frame.Text) handleLobby(json.decodeFromString<LobbyServerMessage>(frame.readText()))
                    }
                }
            } catch (e: Exception) {
                status = "Lobby disconnected: ${e.message}"
                screen = Screen.CONNECT
            }
        }
    }

    private fun handleLobby(message: LobbyServerMessage) {
        when (message) {
            is LobbyServerMessage.Welcome -> { myId = message.playerId; screen = Screen.LOBBY; status = "In lobby" }
            is LobbyServerMessage.Bots -> bots = message.bots
            is LobbyServerMessage.Players -> players = message.players
            is LobbyServerMessage.IncomingChallenge -> challenges = challenges + message.challenge
            is LobbyServerMessage.ChallengeDeclined -> status = "Your challenge was declined"
            is LobbyServerMessage.GameStarted -> { myColour = message.yourColour; openGame(message.gameId, message.yourColour) }
            is LobbyServerMessage.LobbyError -> status = "Error: ${message.message}"
        }
    }

    fun challengeBot(botId: String, colour: String) =
        sendLobby(LobbyClientMessage.ChallengeBot(botId = botId, colour = colour))

    fun challengePlayer(opponentId: String, colour: String) =
        sendLobby(LobbyClientMessage.ChallengePlayer(opponentId = opponentId, colour = colour))

    fun accept(challengeId: String) {
        challenges = challenges.filterNot { it.id == challengeId }
        sendLobby(LobbyClientMessage.AcceptChallenge(challengeId))
    }

    fun decline(challengeId: String) {
        challenges = challenges.filterNot { it.id == challengeId }
        sendLobby(LobbyClientMessage.DeclineChallenge(challengeId))
    }

    private fun sendLobby(message: LobbyClientMessage) {
        scope.launch { lobby?.send(Frame.Text(json.encodeToString(message))) }
    }

    private fun openGame(gameId: String, colour: String) {
        game = null
        selected = null
        screen = Screen.GAME
        scope.launch {
            try {
                http.webSocket("$baseWs/ws/games/$gameId?colour=$colour") {
                    gameSocket = this
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        when (val message = json.decodeFromString<ServerMessage>(frame.readText())) {
                            is ServerMessage.State -> game = message.game
                            is ServerMessage.GameOver -> status = "Game over: ${message.reason}"
                            is ServerMessage.Error -> status = message.message
                        }
                    }
                }
            } catch (e: Exception) {
                status = "Game disconnected: ${e.message}"
            }
        }
    }

    /** Click-to-move: first click selects a source square, the second attempts a move to the target. */
    fun clickSquare(square: Square) {
        val current = game ?: return
        if (current.sideToMove != myColour) { status = "Not your turn"; return }

        val from = selected
        if (from == null) {
            selected = square
            return
        }
        if (from == square) {
            selected = null
            return
        }
        val uci = resolveMove(current.legalMoves, from.toString(), square.toString())
        if (uci != null) {
            scope.launch { gameSocket?.send(Frame.Text(json.encodeToString<ClientMessage>(ClientMessage.MakeMove(uci)))) }
            selected = null
        } else {
            selected = square   // not a legal target; treat as reselect
        }
    }

    fun resign() {
        scope.launch { gameSocket?.send(Frame.Text(json.encodeToString<ClientMessage>(ClientMessage.Resign))) }
    }

    fun leaveGame() {
        scope.launch { gameSocket?.close() }
        gameSocket = null
        game = null
        selected = null
        screen = Screen.LOBBY
    }
}

/**
 * Picks the legal UCI move for a [from]->[to] drag from the server's legal-move list, preferring a
 * queen promotion when the same squares offer several promotions. Returns null if no move matches.
 * Pure (no UI/network) so it is unit-tested directly.
 */
internal fun resolveMove(legalMoves: List<String>, from: String, to: String): String? {
    val candidates = legalMoves.filter { it.startsWith(from + to) }
    return candidates.firstOrNull { it.endsWith("q") } ?: candidates.firstOrNull()
}
