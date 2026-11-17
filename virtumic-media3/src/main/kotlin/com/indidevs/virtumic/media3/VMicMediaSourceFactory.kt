package com.indidevs.virtumic.media3

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import com.indidevs.virtumic.media3.sources.SilentMediaSource

/**
 * A custom [MediaSource.Factory] that handles Virtual Mic specific media items.
 * If a [MediaItem] has an "appaudio" scheme, it creates a [SilentMediaSource].
 * Otherwise, it delegates to [DefaultMediaSourceFactory].
 */
@OptIn(UnstableApi::class)
class VMicMediaSourceFactory(context: Context) : MediaSource.Factory {
    private val defaultFactory = DefaultMediaSourceFactory(context)

    override fun setDrmSessionManagerProvider(
        drmSessionManagerProvider: androidx.media3.exoplayer.drm.DrmSessionManagerProvider
    ): MediaSource.Factory {
        defaultFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(
        loadErrorHandlingPolicy: androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
    ): MediaSource.Factory {
        defaultFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        return this
    }

    override fun getSupportedTypes(): IntArray = defaultFactory.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val uri = mediaItem.localConfiguration?.uri
        return if (uri?.scheme == "appaudio") {
            SilentMediaSource(mediaItem)
        } else {
            defaultFactory.createMediaSource(mediaItem)
        }
    }
}
