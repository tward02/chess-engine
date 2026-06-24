package com.tward.server

import com.tward.engine.board.Colour
import com.tward.shared.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private val json = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") { module() }.start(wait = true)
}

fun Application.module() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val registry = GameRegistry(scope)
    val lobby = LobbyManager(registry, scope)
    monitor.subscribe(ApplicationStopped) { scope.cancel() }

    install(ContentNegotiation) { json(json) }
    install(WebSockets)
    install(CallLogging)
    install(StatusPages) {
        exception<GameException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "bad request"))
        }
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(cause.message ?: "internal error"))
        }
    }

    routing {
        get("/healthz") { call.respondText("ok") }

        // The catalog of bots a player can challenge.
        get("/api/bots") { call.respond(BotCatalog.infos()) }

        // Create a game against a catalog bot (human-vs-human games are created via the lobby).
        post("/api/games") {
            val request = call.receive<CreateGameRequest>()
            val spec = BotCatalog.get(request.botId)
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Unknown bot: ${request.botId}"))

            val human = Participant.Human(playerId = "rest", name = "You")
            val bot = Participant.Bot(spec)
            val (white, black) =
                if (request.playerColour.lowercase() == "black") bot to human else human to bot

            val session = registry.create(white, black, request.initialTimeMillis, request.incrementMillis)
            call.respond(session.start())   // start lets the bot move first if it has White
        }

        get("/api/games/{id}") {
            val session = registry.get(call.parameters["id"].orEmpty())
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("no such game"))
            call.respond(session.state())
        }

        // Convenience move endpoint for single-human bot games; multiplayer games move over the socket.
        post("/api/games/{id}/moves") {
            val session = registry.get(call.parameters["id"].orEmpty())
                ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("no such game"))
            val colour = session.soleHumanColour()
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Use the WebSocket to move in this game")
                )
            val move = call.receive<MoveRequest>()
            call.respond(session.submitMove(move.uci, colour))
        }

        // Live game stream. `?colour=white|black` identifies the mover; omit it to spectate.
        webSocket("/ws/games/{id}") {
            val session = registry.get(call.parameters["id"].orEmpty()) ?: run {
                send(Frame.Text(json.encodeToString<ServerMessage>(ServerMessage.Error("no such game"))))
                return@webSocket
            }
            val colour = when (call.request.queryParameters["colour"]?.lowercase()) {
                "white" -> Colour.WHITE
                "black" -> Colour.BLACK
                else -> null
            }

            val pump = launch {
                session.events.collect { message -> send(Frame.Text(json.encodeToString(message))) }
            }
            try {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    runCatching {
                        when (val message = json.decodeFromString<ClientMessage>(frame.readText())) {
                            is ClientMessage.MakeMove -> {
                                val mover = colour ?: throw GameException("Spectators cannot move")
                                session.submitMove(message.uci, mover)
                            }

                            ClientMessage.Resign -> {
                                val mover = colour ?: throw GameException("Spectators cannot resign")
                                session.resign(mover)
                            }
                        }
                    }.onFailure {
                        send(Frame.Text(json.encodeToString<ServerMessage>(ServerMessage.Error(it.message ?: "error"))))
                    }
                }
            } finally {
                pump.cancel()
            }
        }

        // Lobby: presence + challenges.
        webSocket("/ws/lobby") {
            val player = lobby.register()
            val pump = launch {
                for (message in player.outbox) send(Frame.Text(json.encodeToString(message)))
            }
            try {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    runCatching {
                        when (val message = json.decodeFromString<LobbyClientMessage>(frame.readText())) {
                            is LobbyClientMessage.Join -> lobby.join(player, message.name)
                            is LobbyClientMessage.ChallengePlayer -> lobby.challengePlayer(player, message)
                            is LobbyClientMessage.ChallengeBot -> lobby.challengeBot(player, message)
                            is LobbyClientMessage.AcceptChallenge -> lobby.accept(player, message.challengeId)
                            is LobbyClientMessage.DeclineChallenge -> lobby.decline(player, message.challengeId)
                        }
                    }.onFailure {
                        player.outbox.trySend(LobbyServerMessage.LobbyError(it.message ?: "error"))
                    }
                }
            } finally {
                lobby.disconnect(player)
                pump.cancel()
            }
        }
    }
}
