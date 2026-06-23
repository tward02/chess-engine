package com.tward.client.view

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.tward.client.model.ChessClient
import com.tward.shared.BotInfo
import com.tward.shared.ChallengeInfo
import com.tward.shared.PlayerInfo
import com.tward.shared.PlayerStatus
import com.tward.ui.board.ChessTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ClientViewTest {

    private fun newClient() = ChessClient(CoroutineScope(Dispatchers.Unconfined))

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
