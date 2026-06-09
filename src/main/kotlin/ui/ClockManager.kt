package com.tward.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import com.tward.engine.board.Colour

data class TimeControl(
    val initialMillis: Long,
    val incrementMillis: Long
)

class ClockManager(
    private val control: TimeControl,
    private var activeColor: Colour = Colour.WHITE,
    private val now: () -> Long = System::currentTimeMillis
) {

    var whiteMillis by mutableLongStateOf(control.initialMillis)

    var blackMillis by mutableLongStateOf(control.initialMillis)

    var stopped = false

    private var moveStarted = now()

    fun onMovePlayed() {

        val elapsed = now() - moveStarted

        if (activeColor == Colour.WHITE) {
            whiteMillis -= elapsed
            whiteMillis += control.incrementMillis
            whiteMillis = whiteMillis.coerceAtLeast(0)
        } else {
            blackMillis -= elapsed
            blackMillis += control.incrementMillis
            blackMillis = blackMillis.coerceAtLeast(0)
        }

        activeColor = activeColor.opposite()
        moveStarted = now()
    }

    fun currentWhite(): Long {
        return if (activeColor == Colour.WHITE && !stopped) {
            (whiteMillis - (now() - moveStarted)).coerceAtLeast(0)
        } else {
            whiteMillis.coerceAtLeast(0)
        }
    }

    fun currentBlack(): Long {
        return if (activeColor == Colour.BLACK && !stopped) {
            (blackMillis - (now() - moveStarted)).coerceAtLeast(0)
        } else {
            blackMillis.coerceAtLeast(0)
        }
    }

    fun stopClock() {
        if (!stopped) {
            val elapsed = now() - moveStarted

            if (activeColor == Colour.WHITE) {
                whiteMillis = (whiteMillis - elapsed).coerceAtLeast(0)
            } else {
                blackMillis = (blackMillis - elapsed).coerceAtLeast(0)
            }

            stopped = true
        }
    }
}
