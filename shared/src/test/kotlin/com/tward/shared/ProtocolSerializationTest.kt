package com.tward.shared

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProtocolSerializationTest {

    private val json = Json { classDiscriminator = "type"; ignoreUnknownKeys = true }

    @Test
    fun `game state round-trips through json`() {
        val state = GameStateDto(
            gameId = "abc", fen = "startpos", sideToMove = "white",
            lastMove = "e2e4", whiteMillis = 300_000, blackMillis = 299_000,
            status = GameStatus.IN_PROGRESS, legalMoves = listOf("e2e4", "d2d4")
        )
        val decoded = json.decodeFromString<GameStateDto>(json.encodeToString(state))
        assertEquals(state, decoded)
    }

    @Test
    fun `client messages are polymorphic on a type discriminator`() {
        val encoded = json.encodeToString<ClientMessage>(ClientMessage.MakeMove("g1f3"))
        assertTrue(encoded.contains("\"type\":\"move\""), "expected a discriminated type tag: $encoded")

        val decoded = json.decodeFromString<ClientMessage>(encoded)
        assertEquals(ClientMessage.MakeMove("g1f3"), decoded)
    }

    @Test
    fun `server messages are polymorphic`() {
        val msg: ServerMessage = ServerMessage.GameOver(GameStatus.WHITE_WON, "checkmate")
        val decoded = json.decodeFromString<ServerMessage>(json.encodeToString(msg))
        assertEquals(msg, decoded)
    }
}
