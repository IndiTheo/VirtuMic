package com.indidevs.virtumic.core

import android.media.AudioRecord

/**
 * INTERNAL: A hardware-level session for capturing audio from the system or specific apps.
 */
internal interface CaptureSession : AutoCloseable {
    /**
     * Creates an [AudioRecord] for this capture session.
     */
    fun createRecord(): AudioRecord?
}
