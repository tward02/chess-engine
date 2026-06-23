package ui.view.game

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.tward.engine.board.Colour
import com.tward.ui.board.CapturedPieces
import com.tward.ui.model.ClockManager
import com.tward.ui.model.TimeControl
import com.tward.ui.view.game.ChessClock
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ComposeViewTest {

    @Test
    fun `clock renders the formatted remaining time`() = runComposeUiTest {

        // Disable the auto clock so the perpetual ticking effect doesn't keep the test busy
        mainClock.autoAdvance = false

        val clock = ClockManager(
            control = TimeControl(initialMillis = 65_000, incrementMillis = 0),
            now = { 0L }
        )

        setContent {
            ChessClock(clockManager = clock, colour = Colour.WHITE, isActive = true)
        }

        onNodeWithText("1:05").assertIsDisplayed()
    }

    @Test
    fun `captured pieces panel shows the material advantage`() = runComposeUiTest {

        // An empty piece list keeps the test from loading image resources; only the badge renders
        setContent {
            CapturedPieces(pieces = emptyList(), advantage = 5)
        }

        onNodeWithText("+5").assertIsDisplayed()
    }
}
