package com.tward.server

import com.tward.shared.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ServerTest {

    private val json = Json { classDiscriminator = "type"; ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `health endpoint responds`() = testApplication {
        application { module() }
        assertEquals(HttpStatusCode.OK, client.get("/healthz").status)
    }

    @Test
    fun `lists the bot catalog`() = testApplication {
        application { module() }
        val client = createClient { install(ContentNegotiation) { json(json) } }
        val bots: List<BotInfo> = client.get("/api/bots").body()
        assertTrue(bots.size >= 10)
        assertTrue(bots.any { it.id == "randall" })
    }

    @Test
    fun `create a bot game then play a move gets a bot reply`() = testApplication {
        application { module() }
        val client = createClient { install(ContentNegotiation) { json(json) } }

        val created = client.post("/api/games") {
            contentType(ContentType.Application.Json)
            setBody(CreateGameRequest(botId = "randall", playerColour = "white"))
        }
        assertEquals(HttpStatusCode.OK, created.status)
        val start: GameStateDto = created.body()
        assertEquals(GameStatus.IN_PROGRESS, start.status)
        assertEquals("white", start.sideToMove)
        assertTrue(start.legalMoves.contains("e2e4"))

        val after: GameStateDto = client.post("/api/games/${start.gameId}/moves") {
            contentType(ContentType.Application.Json)
            setBody(MoveRequest("e2e4"))
        }.body()

        // White moved, the bot replied as Black, so it's White to move again with Black's move last.
        assertEquals("white", after.sideToMove)
        assertNotNull(after.lastMove)
        assertTrue(after.lastMove != "e2e4", "last move should be the bot's reply")
    }

    @Test
    fun `a bot playing white moves first on creation`() = testApplication {
        application { module() }
        val client = createClient { install(ContentNegotiation) { json(json) } }
        // Human takes Black, so the bot (White) should already have moved by the time we get the state.
        val start: GameStateDto = client.post("/api/games") {
            contentType(ContentType.Application.Json)
            setBody(CreateGameRequest(botId = "randall", playerColour = "black"))
        }.body()
        assertEquals("black", start.sideToMove)
        assertNotNull(start.lastMove)
    }

    @Test
    fun `unknown bot id is rejected`() = testApplication {
        application { module() }
        val client = createClient { install(ContentNegotiation) { json(json) } }
        val res = client.post("/api/games") {
            contentType(ContentType.Application.Json)
            setBody(CreateGameRequest(botId = "does-not-exist"))
        }
        assertEquals(HttpStatusCode.BadRequest, res.status)
    }

    @Test
    fun `an illegal move is rejected`() = testApplication {
        application { module() }
        val client = createClient { install(ContentNegotiation) { json(json) } }
        val start: GameStateDto = client.post("/api/games") {
            contentType(ContentType.Application.Json)
            setBody(CreateGameRequest(botId = "randall"))
        }.body()

        val res = client.post("/api/games/${start.gameId}/moves") {
            contentType(ContentType.Application.Json)
            setBody(MoveRequest("e2e5"))   // not a legal opening move
        }
        assertEquals(HttpStatusCode.BadRequest, res.status)
    }

    @Test
    fun `a player who runs out of time loses even without moving`() = testApplication {
        application { module() }
        val client = createClient {
            install(ContentNegotiation) { json(json) }
            install(WebSockets)
        }
        // Human is White with a 100ms clock; the bot is Black. White never moves, so White must flag.
        val start: GameStateDto = client.post("/api/games") {
            contentType(ContentType.Application.Json)
            setBody(CreateGameRequest(botId = "randall", playerColour = "white", initialTimeMillis = 100))
        }.body()

        var finalStatus: GameStatus? = null
        client.webSocket("/ws/games/${start.gameId}?colour=white") {
            for (frame in incoming) {
                if (frame !is Frame.Text) continue
                val message = json.decodeFromString<ServerMessage>(frame.readText())
                if (message is ServerMessage.GameOver) {
                    finalStatus = message.status
                    break
                }
            }
        }
        assertEquals(GameStatus.BLACK_WON, finalStatus)   // White flagged, so Black wins
    }
}
