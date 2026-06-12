package ui

import com.tward.engine.board.Colour
import com.tward.ui.model.ClockManager
import com.tward.ui.model.TimeControl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClockManagerTest {

    @Test
    fun `white clock counts down while white is active`() {

        var currentTime = 0L

        val manager = ClockManager(
            TimeControl(
                initialMillis = 60_000,
                incrementMillis = 0
            ),
            now = { currentTime }
        )

        currentTime = 5_000

        assertEquals(55_000, manager.currentWhite())
        assertEquals(60_000, manager.currentBlack())
    }

    @Test
    fun `white move subtracts elapsed time`() {

        var currentTime = 0L

        val manager = ClockManager(
            TimeControl(
                initialMillis = 60_000,
                incrementMillis = 0
            ),
            now = { currentTime }
        )

        currentTime = 4_000

        manager.onMovePlayed()

        assertEquals(56_000, manager.whiteMillis)
        assertEquals(60_000, manager.blackMillis)
    }

    @Test
    fun `increment is added after move`() {

        var currentTime = 0L

        val manager = ClockManager(
            TimeControl(
                initialMillis = 60_000,
                incrementMillis = 2_000
            ),
            now = { currentTime }
        )

        currentTime = 5_000

        manager.onMovePlayed()

        assertEquals(57_000, manager.whiteMillis)
    }

    @Test
    fun `active player switches after move`() {

        var currentTime = 0L

        val manager = ClockManager(
            TimeControl(
                initialMillis = 60_000,
                incrementMillis = 0
            ),
            now = { currentTime }
        )

        manager.onMovePlayed()

        currentTime = 3_000

        assertEquals(57_000, manager.currentBlack())
        assertEquals(60_000, manager.currentWhite())
    }

    @Test
    fun `multiple moves update both clocks correctly`() {

        var currentTime = 0L

        val manager = ClockManager(
            TimeControl(
                initialMillis = 60_000,
                incrementMillis = 1_000
            ),
            now = { currentTime }
        )

        currentTime = 5_000
        manager.onMovePlayed()

        currentTime = 8_000
        manager.onMovePlayed()

        assertEquals(56_000, manager.whiteMillis)
        assertEquals(58_000, manager.blackMillis)
    }

    @Test
    fun `current white never goes below zero`() {

        var currentTime = 0L

        val manager = ClockManager(
            TimeControl(
                initialMillis = 1_000,
                incrementMillis = 0
            ),
            now = { currentTime }
        )

        currentTime = 5_000

        assertEquals(0, manager.currentWhite())
    }

    @Test
    fun `current black never goes below zero`() {

        var currentTime = 0L

        val manager = ClockManager(
            TimeControl(
                initialMillis = 1_000,
                incrementMillis = 0
            ),
            activeColor = Colour.BLACK,
            now = { currentTime }
        )

        currentTime = 5_000

        assertEquals(0, manager.currentBlack())
    }

    @Test
    fun `stored white time never goes below zero after move`() {

        var currentTime = 0L

        val manager = ClockManager(
            TimeControl(
                initialMillis = 1_000,
                incrementMillis = 0
            ),
            now = { currentTime }
        )

        currentTime = 5_000

        manager.onMovePlayed()

        assertEquals(0, manager.whiteMillis)
    }

    @Test
    fun `stored black time never goes below zero after move`() {

        var currentTime = 0L

        val manager = ClockManager(
            TimeControl(
                initialMillis = 60_000,
                incrementMillis = 0
            ),
            now = { currentTime }
        )

        manager.onMovePlayed()

        currentTime = 100_000

        manager.onMovePlayed()

        assertEquals(0, manager.blackMillis)
    }

    @Test
    fun `stop clock freezes white clock`() {

        var currentTime = 0L

        val manager = ClockManager(
            TimeControl(
                initialMillis = 60_000,
                incrementMillis = 0
            ),
            now = { currentTime }
        )

        currentTime = 5_000

        val beforeStop = manager.currentWhite()

        manager.stopClock()

        currentTime = 20_000

        assertEquals(beforeStop, manager.currentWhite())
    }

    @Test
    fun `stop clock sets stopped flag`() {

        val manager = ClockManager(
            TimeControl(
                initialMillis = 60_000,
                incrementMillis = 0
            )
        )

        manager.stopClock()

        assertTrue(manager.stopped)
    }
}