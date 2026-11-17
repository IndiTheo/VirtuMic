package com.indidevs.virtumic.service.session

/**
 * Public contract for interacting with the VirtuMic Media Session Service.
 * Third-party controllers should use these constants to ensure compatibility.
 */
object VMicServiceContract {
    /**
     * Key for the microphone ID in [MediaSession.ConnectionResult.connectionHints].
     * Allows controllers to specify which virtual microphone instance they want to control.
     */
    const val CONNECTION_HINT_MIC_ID = "MIC_ID"

    /**
     * Default microphone ID for virtual microphone sessions.
     */
    const val DEFAULT_MIC_ID = ""

    /**
     * URI scheme for targeted application audio capture.
     * Format: appaudio://capture?pkg={package_name}
     */
    const val URI_SCHEME_APPAUDIO = "appaudio"

    /**
     * Query parameter for the target package name in appaudio URIs.
     */
    const val URI_PARAM_PACKAGE = "pkg"

    /**
     * Key for [AudioConfig] in MediaItem metadata extras.
     */
    const val EXTRA_AUDIO_CONFIG = "com.indidevs.virtumic.extra.AUDIO_CONFIG"
}
