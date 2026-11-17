package com.indidevs.virtumic.service.model

import android.media.AudioFormat
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Technical configuration for the virtual microphone pipe.
 * Defines "How" the audio should be processed.
 *
 * Note: Format encoding is strictly locked to 16-bit PCM (ENCODING_PCM_16BIT) because
 * Android's REMOTE_SUBMIX and audio policy injection pipelines do not support floating-point
 * or 32-bit capture/injection.
 */
@Parcelize
data class AudioConfig(
    val format: AudioFormat,
    val targetPresets: IntArray
) : Parcelable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioConfig
        if (format != other.format) return false
        if (!targetPresets.contentEquals(other.targetPresets)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = format.hashCode()
        result = 31 * result + targetPresets.contentHashCode()
        return result
    }
}
