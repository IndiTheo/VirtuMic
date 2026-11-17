package com.indidevs.virtumic.service.integration

import com.indidevs.virtumic.core.VMicEngine

/**
 * A dependency injection interface implemented by the Application class to provide
 * the [VMicEngine] to background services without a direct module dependency.
 */
interface VMicEngineProvider {
    /**
     * Returns the current [VMicEngine] if available.
     */
    val engine: VMicEngine?
}
