package com.indidevs.virtumic.domain.model

import android.net.Uri
import java.time.Duration
import java.util.Locale

data class FileSourceInfo(
    val uri: Uri,
    val fileName: String,
    val format: String,
    val duration: Duration,
    val sampleRate: Int,
    val channelCount: Int,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null
) {
    val formattedDuration: String
        get() = duration.formatDuration()
}

fun Duration.formatDuration(): String {
    val seconds = this.seconds
    val absSeconds = kotlin.math.abs(seconds)
    val positive = String.format(
        Locale.getDefault(),
        "%02d:%02d",
        absSeconds / 60,
        absSeconds % 60
    )
    return if (seconds < 0) "-$positive" else positive
}
