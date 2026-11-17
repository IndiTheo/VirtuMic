package com.indidevs.virtumic.media3.sources

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ForwardingTimeline
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.media3.exoplayer.source.WrappingMediaSource

/**
 * A [WrappingMediaSource] that delegates silence generation to ExoPlayer's [SilenceMediaSource]
 * while preserving custom [MediaItem] metadata and window properties.
 */
@OptIn(UnstableApi::class)
class SilentMediaSource(
    private val mediaItem: MediaItem
) : WrappingMediaSource(SilenceMediaSource(Long.MAX_VALUE)) {

    override fun getMediaItem(): MediaItem = mediaItem

    override fun onChildSourceInfoRefreshed(newTimeline: Timeline) {
        val customTimeline = object : ForwardingTimeline(newTimeline) {
            override fun getWindow(
                windowIndex: Int,
                window: Timeline.Window,
                defaultPositionProjectionUs: Long
            ): Timeline.Window {
                val w = super.getWindow(windowIndex, window, defaultPositionProjectionUs)
                w.mediaItem = mediaItem
                return w
            }
        }
        super.onChildSourceInfoRefreshed(customTimeline)
    }
}
