package com.tward.server

import com.tward.shared.ClientMessage
import com.tward.shared.CreateGameRequest
import com.tward.shared.ErrorResponse
import com.tward.shared.MoveRequest
import com.tward.shared.ServerMessage
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
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
    val registry = GameRegistry(InProcessEngineService())

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

        // Create a game (vs a bot by default). Returns the initial state.
        post("/api/games") {
            val request = call.receive<CreateGameRequest>()
            call.respond(registry.create(request).state())
        }

        // Current snapshot of a game.
        get("/api/games/{id}") {
            val session = registry.get(call.parameters["id"].orEmpty())
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("no such game"))
            call.respond(session.state())
        }

        // Play a move (and, in a bot game, receive the bot's reply in the returned state).
        post("/api/games/{id}/moves") {
            val session = registry.get(call.parameters["id"].orEmpty())
                ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("no such game"))
            val move = call.receive<MoveRequest>()
            call.respond(session.submitMove(move.uci))
        }

        // Live game stream: pushes a State message on every change; accepts moves/resignations.
        webSocket("/ws/games/{id}") {
            val session = registry.get(call.parameters["id"].orEmpty()) ?: run {
                send(Frame.Text(json.encodeToString<ServerMessage>(ServerMessage.Error("no such game"))))
                return@webSocket
            }

            val pump = launch {
                session.events.collect { message -> send(Frame.Text(json.encodeToString(message))) }
            }
            try {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    runCatching {
                        when (val message = json.decodeFromString<ClientMessage>(frame.readText())) {
                            is ClientMessage.MakeMove -> session.submitMove(message.uci)
                            ClientMessage.Resign -> session.resign()
                        }
                    }.onFailure {
                        send(Frame.Text(json.encodeToString<ServerMessage>(ServerMessage.Error(it.message ?: "error"))))
                    }
                }
            } finally {
                pump.cancel()
            }
        }
    }
}
