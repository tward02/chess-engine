package com.tward.server

import com.tward.shared.BotDifficulty
import com.tward.shared.CreateGameRequest
import com.tward.shared.GameStateDto
import com.tward.shared.GameStatus
import com.tward.shared.MoveRequest
import com.tward.shared.PlayerKind
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
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
    fun `create a bot game then play a move gets a bot reply`() = testApplication {
        application { module() }
        val client = createClient { install(ContentNegotiation) { json(json) } }

        val created = client.post("/api/games") {
            contentType(ContentType.Application.Json)
            setBody(CreateGameRequest(opponent = PlayerKind.BOT, difficulty = BotDifficulty.EASY, playerColour = "white"))
        }
        assertEquals(HttpStatusCode.OK, created.status)
        val start: GameStateDto = created.body()
        assertEquals(GameStatus.IN_PROGRESS, start.status)
        assertEquals("white", start.sideToMove)
        assertTrue(start.legalMoves.contains("e2e4"))

        val afterMove = client.post("/api/games/${start.gameId}/moves") {
            contentType(ContentType.Application.Json)
            setBody(MoveRequest("e2e4"))
        }
        assertEquals(HttpStatusCode.OK, afterMove.status)
        val state: GameStateDto = afterMove.body()

        // White moved, the bot replied as Black, so it's White to move again and the last move is Black's.
        assertEquals("white", state.sideToMove)
        assertNotNull(state.lastMove)
        assertTrue(state.lastMove != "e2e4", "last move should be the bot's reply, not White's e2e4")
    }

    @Test
    fun `an illegal move is rejected`() = testApplication {
        application { module() }
        val client = createClient { install(ContentNegotiation) { json(json) } }

        val start: GameStateDto = client.post("/api/games") {
            contentType(ContentType.Application.Json)
            setBody(CreateGameRequest(difficulty = BotDifficulty.EASY))
        }.body()

        val rejected = client.post("/api/games/${start.gameId}/moves") {
            contentType(ContentType.Application.Json)
            setBody(MoveRequest("e2e5"))   // not a legal opening move
        }
        assertEquals(HttpStatusCode.BadRequest, rejected.status)
    }
}
