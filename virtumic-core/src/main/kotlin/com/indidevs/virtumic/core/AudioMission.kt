package com.indidevs.virtumic.core

import android.media.AudioFormat
import android.media.MediaRecorder
import java.util.Objects

/**
 * Technical blueprint for any audio policy operation.
 */
class AudioMission(
    val format: AudioFormat,
    presets: IntArray? = null,
    val timeoutMillis: Long = 5000L
) {
    val presets: IntArray = if (presets != null && presets.isNotEmpty()) {
        presets
    } else {
        DEFAULT_PRESETS
    }

    /**
     * Returns a copy of this mission with a new [AudioFormat].
     */
    fun withFormat(newFormat: AudioFormat): AudioMission {
        return AudioMission(newFormat, presets, timeoutMillis)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioMission) return false
        return format == other.format &&
            presets.contentEquals(other.presets) &&
            timeoutMillis == other.timeoutMillis
    }

    override fun hashCode(): Int = Objects.hash(format, presets.contentHashCode(), timeoutMillis)

    companion object {
        val DEFAULT_PRESETS = intArrayOf(
            MediaRecorder.AudioSource.DEFAULT,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.CAMCORDER,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.VOICE_PERFORMANCE
        )
    }
}
