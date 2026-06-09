package com.tward.ui

import com.tward.engine.board.Move
import java.io.BufferedInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import kotlin.concurrent.thread

fun playMoveSound(move: Move) {
    if (move.capturedPiece != null) {
        playSound("/sounds/take.wav")
    } else {
        playSound("/sounds/move.wav")
    }
}

fun playDoneSound() {
    playSound("/sounds/done.mp3")
}

//TODO check sound

fun playSound(resourcePath: String) {
    thread(isDaemon = true) {
        try {
            val stream = object {}.javaClass.getResourceAsStream(resourcePath)
                ?: error("Resource not found: $resourcePath")

            val audioInputStream =
                AudioSystem.getAudioInputStream(BufferedInputStream(stream))

            val clip: Clip = AudioSystem.getClip()
            clip.open(audioInputStream)

            clip.addLineListener {
                if (it.type == javax.sound.sampled.LineEvent.Type.STOP) {
                    clip.close()
                }
            }

            clip.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
