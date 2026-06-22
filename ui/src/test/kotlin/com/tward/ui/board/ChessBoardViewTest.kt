package com.tward.ui.board

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.tward.engine.board.Square
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class ChessBoardViewTest {

    // Empty board (pieceAt = { null }) keeps these tests off the image assets, per the project convention.

    @Test
    fun `clicking a square reports that square`() = runComposeUiTest {
        var clicked: Square? = null
        setContent { ChessBoardView(pieceAt = { null }, onSquareClick = { clicked = it }) }

        onNodeWithTag("e2").performClick()

        assertEquals(Square.fromString("e2"), clicked)
    }

    @Test
    fun `renders a square for every coordinate`() = runComposeUiTest {
        setContent { ChessBoardView(pieceAt = { null }) }

        onNodeWithTag("a1").assertExists()
        onNodeWithTag("h8").assertExists()
        onNodeWithTag("d5").assertExists()
    }

    @Test
    fun `clicks are ignored when clicking is disabled`() = runComposeUiTest {
        var clicked: Square? = null
        setContent {
            ChessBoardView(pieceAt = { null }, clickEnabled = false, onSquareClick = { clicked = it })
        }

        onNodeWithTag("e2").performClick()

        assertNull(clicked)
    }
}
