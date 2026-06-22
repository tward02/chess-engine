package com.tward.server

import com.tward.shared.LobbyClientMessage
import com.tward.shared.LobbyServerMessage
import io.ktor.client.plugins.websocket.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val lobbyJson = Json { classDiscriminator = "type"; ignoreUnknownKeys = true; encodeDefaults = true }

private suspend fun DefaultClientWebSocketSession.sendLobby(message: LobbyClientMessage) =
    send(Frame.Text(lobbyJson.encodeToString(message)))

private suspend inline fun <reified T : LobbyServerMessage> DefaultClientWebSocketSession.await(): T {
    while (true) {
        val frame = incoming.receive()
        if (frame is Frame.Text) {
            val message = lobbyJson.decodeFromString<LobbyServerMessage>(frame.readText())
            if (message is T) return message
        }
    }
}

class LobbyTest {

    @Test
    fun `joining returns welcome, bots and a player list including yourself`() = testApplication {
        application { module() }
        val client = createClient { install(WebSockets) }

        client.webSocket("/ws/lobby") {
            sendLobby(LobbyClientMessage.Join("Solo"))
            assertTrue(await<LobbyServerMessage.Welcome>().playerId.isNotBlank())
            assertTrue(await<LobbyServerMessage.Bots>().bots.isNotEmpty())
            assertTrue(await<LobbyServerMessage.Players>().players.any { it.name == "Solo" })
        }
    }

    @Test
    fun `challenging a bot immediately starts a game`() = testApplication {
        application { module() }
        val client = createClient { install(WebSockets) }

        client.webSocket("/ws/lobby") {
            sendLobby(LobbyClientMessage.Join("Solo"))
            await<LobbyServerMessage.Welcome>()
            sendLobby(LobbyClientMessage.ChallengeBot(botId = "randall", colour = "white"))
            val started = await<LobbyServerMessage.GameStarted>()
            assertTrue(started.gameId.isNotBlank())
            assertEquals("white", started.yourColour)
        }
    }

    @Test
    fun `one player can challenge another and both start the same game`() = testApplication {
        application { module() }
        val client = createClient { install(WebSockets) }

        client.webSocket("/ws/lobby") {              // Alice
            val alice = this
            alice.sendLobby(LobbyClientMessage.Join("Alice"))
            alice.await<LobbyServerMessage.Welcome>()

            client.webSocket("/ws/lobby") {          // Bob (nested so both stay connected)
                val bob = this
                bob.sendLobby(LobbyClientMessage.Join("Bob"))
                val bobId = bob.await<LobbyServerMessage.Welcome>().playerId

                alice.sendLobby(LobbyClientMessage.ChallengePlayer(opponentId = bobId, colour = "white"))
                val challenge = bob.await<LobbyServerMessage.IncomingChallenge>().challenge
                bob.sendLobby(LobbyClientMessage.AcceptChallenge(challenge.id))

                val aliceStart = alice.await<LobbyServerMessage.GameStarted>()
                val bobStart = bob.await<LobbyServerMessage.GameStarted>()
                assertEquals(aliceStart.gameId, bobStart.gameId)
                assertEquals("white", aliceStart.yourColour)
                assertEquals("black", bobStart.yourColour)
            }
        }
    }
}
