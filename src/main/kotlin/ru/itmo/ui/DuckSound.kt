package ru.itmo.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import ru.itmo.config.DuckSettings
import java.io.BufferedInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineEvent

/** Java Sound can't decode MP3, so the sound is packaged as WAV, see docs/assets.md. */
private const val QUACK_RESOURCE = "/duck/sounds/quack.wav"

object DuckSound {
    /** Quacks unless the user turned it off in the settings. */
    fun quack() {
        if (!DuckSettings.getInstance().state.quackOnClick) return
        ApplicationManager.getApplication().executeOnPooledThread { play(QUACK_RESOURCE) }
    }

    private fun play(resource: String) {
        try {
            val stream = DuckSound::class.java.getResourceAsStream(resource)
                ?: error("Duck sound resource not found: $resource")
            AudioSystem.getAudioInputStream(BufferedInputStream(stream)).use { audio ->
                val clip = AudioSystem.getClip()
                // release the audio line once the sound is over, otherwise every quack leaks one
                clip.addLineListener { if (it.type == LineEvent.Type.STOP) clip.close() }
                clip.open(audio)
                clip.start()
            }
        } catch (e: Exception) {
            // e.g. no audio device: the duck just stays silent
            thisLogger().info("Can't play $resource", e)
        }
    }
}
