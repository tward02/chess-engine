package com.tward.ui.board

import java.io.BufferedInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineEvent
import kotlin.concurrent.thread

/**
 * Plays the board sound effects (move / capture / check / game-over) from the shared `sounds/`
 * assets. Best-effort and fire-and-forget: each clip plays on a daemon thread and a missing or
 * unplayable sound is swallowed so it never disrupts the UI. Shared by the desktop game UI and the
 * server test client.
 */
object Sounds {

    fun playMove(isCapture: Boolean, isCheck: Boolean) {
        play(
            when {
                isCheck -> "/sounds/check.wav"
                isCapture -> "/sounds/take.wav"
                else -> "/sounds/move.wav"
            }
        )
    }

    fun playGameOver() = play("/sounds/done.wav")

    private fun play(resourcePath: String) {
        thread(isDaemon = true) {
            try {
                val stream = Sounds::class.java.getResourceAsStream(resourcePath) ?: return@thread
                val audio = AudioSystem.getAudioInputStream(BufferedInputStream(stream))
                val clip = AudioSystem.getClip()
                clip.open(audio)
                clip.addLineListener { if (it.type == LineEvent.Type.STOP) clip.close() }
                clip.start()
            } catch (_: Exception) {
                // best-effort; sound is non-essential
            }
        }
    }
}
