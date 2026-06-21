package ui.view.tournament

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.tward.engine.player.bot.RandomBot
import com.tward.engine.tournament.BotSpec
import com.tward.engine.tournament.MultiBotTournament
import com.tward.engine.tournament.format.RoundRobinFormat
import com.tward.ui.view.tournament.MultiBotTournamentView
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class MultiBotTournamentViewTest {

    @Test
    fun `standings table lists contenders and format`() = runComposeUiTest {

        // The view runs perpetual polling effects; pause the clock so the test doesn't hang on them
        mainClock.autoAdvance = false

        val tournament = MultiBotTournament(
            contenders = listOf(
                BotSpec("Alpha") { RandomBot() },
                BotSpec("Beta") { RandomBot() }
            ),
            format = RoundRobinFormat()
        )

        setContent {
            MultiBotTournamentView(tournament)
        }

        onNodeWithText("Round-Robin").assertIsDisplayed()
        onNodeWithText("Alpha").assertIsDisplayed()
        onNodeWithText("Beta").assertIsDisplayed()
        onNodeWithText("Pts").assertIsDisplayed()
    }
}
