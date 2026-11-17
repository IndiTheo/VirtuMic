package com.indidevs.virtumic.core

import android.media.AudioTrack

/**
 * INTERNAL: A hardware-level session for injecting audio into the system.
 */
internal interface PlaybackSession : AutoCloseable {
    /**
     * Creates an [AudioTrack] for this injection session.
     */
    fun createTrack(): AudioTrack?
}
