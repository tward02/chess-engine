package com.tward.engine.game

import com.tward.engine.board.Colour

data class TimeControl(
    val initialMillis: Long,
    val incrementMillis: Long
)

class ClockManager(
    private val control: TimeControl,
    private var activeColor: Colour = Colour.WHITE
) {

    var whiteMillis = control.initialMillis

    var blackMillis = control.initialMillis

    private var moveStarted = System.currentTimeMillis()

    fun onMovePlayed() {

        val now = System.currentTimeMillis()

        val elapsed = now - moveStarted

        if (activeColor == Colour.WHITE) {
            whiteMillis -= elapsed
            whiteMillis += control.incrementMillis
        } else {
            blackMillis -= elapsed
            blackMillis += control.incrementMillis
        }

        activeColor = activeColor.opposite()
        moveStarted = now
    }

    fun currentWhite(): Long {
        return whiteMillis
    }

    fun currentBlack(): Long {
        return blackMillis
    }
}
