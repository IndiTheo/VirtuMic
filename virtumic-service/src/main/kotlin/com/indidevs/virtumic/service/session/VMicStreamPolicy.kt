package com.indidevs.virtumic.service.session

import androidx.media3.common.MediaItem

/**
 * Encapsulates playback and streaming policy rules for MediaItems.
 */
object VMicStreamPolicy {
    /**
     * Determines whether the given media item requires a silent player clock (dummy output)
     * rather than direct audio decoding.
     */
    fun shouldSilencePlayer(mediaItem: MediaItem): Boolean {
        val uri = mediaItem.localConfiguration?.uri ?: return false
        return uri.scheme == VMicServiceContract.URI_SCHEME_APPAUDIO
    }
}
