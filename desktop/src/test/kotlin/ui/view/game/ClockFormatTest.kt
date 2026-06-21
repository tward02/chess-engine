package ui.view.game

import com.tward.ui.view.game.formatClock
import kotlin.test.Test
import kotlin.test.assertEquals

class ClockFormatTest {

    @Test
    fun `minutes and seconds above a minute`() {
        assertEquals("1:05", formatClock(65_000))
    }

    @Test
    fun `tenths shown in the final minute`() {
        assertEquals("0:42.7", formatClock(42_700))
    }

    @Test
    fun `exactly one minute drops the tenths`() {
        assertEquals("1:00", formatClock(60_000))
    }

    @Test
    fun `negative time is clamped to zero`() {
        assertEquals("0:00.0", formatClock(-5_000))
    }
}
