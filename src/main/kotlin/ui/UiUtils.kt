package com.tward.ui

import java.io.BufferedInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import kotlin.concurrent.thread

fun playMoveSound() {
    playSound("/sounds/move.wav")
}

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
