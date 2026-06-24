package com.tward.client.view

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.tward.client.model.ChessClient
import com.tward.engine.board.Square
import com.tward.shared.BotInfo
import com.tward.shared.ChallengeInfo
import com.tward.shared.PlayerInfo
import com.tward.shared.PlayerStatus
import com.tward.ui.board.ChessTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ClientViewTest {

    private fun newClient() = ChessClient(CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun `legal targets are the destinations of moves from the selected square`() {
        val legal = listOf("e2e4", "e2e3", "g1f3", "d2d4")
        assertEquals(
            setOf(Square.fromString("e4"), Square.fromString("e3")),
            legalTargetsFor(legal, Square.fromString("e2"))
        )
    }

    @Test
    fun `promotions to one square collapse to a single target`() {
        val legal = listOf("e7e8q", "e7e8r", "e7e8b", "e7e8n")
        assertEquals(setOf(Square.fromString("e8")), legalTargetsFor(legal, Square.fromString("e7")))
    }

    @Test
    fun `a square with no legal move has no targets`() {
        assertTrue(legalTargetsFor(listOf("e2e4"), Square.fromString("a7")).isEmpty())
    }

    @Test
    fun `connect screen shows the connect control`() = runComposeUiTest {
        val client = newClient()
        setContent { ChessTheme { ConnectScreen(client) } }

        onNodeWithText("Connect to a chess server").assertExists()
        onNodeWithText("Connect").assertExists()
        client.close()
    }

    @Test
    fun `lobby lists a challengeable bot`() = runComposeUiTest {
        val client = newClient()
        client.bots = listOf(BotInfo("greg", "Grandmaster Greg", 2250, "A calm calculator", "Strongest"))
        setContent { ChessTheme { LobbyScreen(client) } }

        onNodeWithText("Grandmaster Greg", substring = true).assertExists()
        onNodeWithText("Play White").assertExists()
        onNodeWithText("Play Black").assertExists()
        client.close()
    }

    @Test
    fun `lobby lists an available player with a challenge button`() = runComposeUiTest {
        val client = newClient()
        client.myId = "me"
        client.players = listOf(PlayerInfo("p2", "Bob", PlayerStatus.AVAILABLE))
        setContent { ChessTheme { LobbyScreen(client) } }

        onNodeWithText("Bob", substring = true).assertExists()
        onNodeWithText("Challenge").assertExists()
        client.close()
    }

    @Test
    fun `declining an incoming challenge from the lobby removes it`() = runComposeUiTest {
        val client = newClient()
        client.myId = "me"
        client.challenges = listOf(ChallengeInfo("c1", "p", "Pat", "me", "white", 60_000, 0))
        setContent { ChessTheme { LobbyScreen(client) } }

        onNodeWithText("Pat challenges you").assertExists()
        onNodeWithText("Decline").performClick()

        assertTrue(client.challenges.isEmpty())
        client.close()
    }

    @Test
    fun `game screen shows a loading message before the first state`() = runComposeUiTest {
        val client = newClient()   // client.game is null
        setContent { ChessTheme { GameScreen(client) } }

        onNodeWithText("Loading game…").assertExists()
        client.close()
    }
}
