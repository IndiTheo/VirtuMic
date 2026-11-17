package com.indidevs.virtumic.domain.model

/**
 * Represents the operational readiness state of the privilege host required for audio streaming.
 */
enum class StreamingPrivilege {
    NOT_READY,
    STARTING,
    READY
}
