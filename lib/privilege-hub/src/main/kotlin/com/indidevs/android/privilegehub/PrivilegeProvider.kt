package com.indidevs.android.privilegehub

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeout

/**
 * A marker interface for any privilege handle.
 */
interface Privilege {
    /**
     * Releases resources associated with this privilege.
     *
     * After calling this method, the privilege should no longer be used. Subsequent calls to
     * [isAlive()] should return false. This method is safe to call multiple times.
     */
    suspend fun release()

    /**
     * Checks if the privilege is still active/valid.
     *
     * @return True if the underlying resource is still available and the privilege can be used;
     *         false if the resource has been revoked, become unavailable, or the privilege
     *         has been released.
     */
    suspend fun isAlive(): Boolean
}

/**
 * A provider capable of acquiring a specific type of [Privilege].
 *
 * Implementations of this interface know how to acquire a particular kind of privilege.
 *
 * End clients should interact with providers only through the [acquire] method. The [onAcquire]
 * method is for provider implementation only and should not be called directly by clients.
 */
interface PrivilegeProvider<out T : Privilege> {
    companion object {
        /** Indicates that no provider is selected. */
        const val ID_NONE = "NONE"

        /** Default timeout for acquisition attempts */
        const val DEFAULT_TIMEOUT_MS = 180_000L
    }

    /** A unique string identifier for this provider. */
    val id: String

    /** A human-readable name for this provider (suitable for display in UI). */
    val name: String

    /**
     * Observable hints about the current progress or status of this provider.
     * These hints can be displayed in the UI to provide feedback during acquisition.
     */
    val hints: StateFlow<Set<PrivilegeHint>> get() = MutableStateFlow(emptySet())

    /**
     * The priority of this provider. Higher values are more preferred
     * during automatic resolution.
     */
    val priority: Int get() = 0

    /**
     * Indicates whether this privilege mode is available on this device.
     *
     * Reflects runtime conditions that affect availability (e.g., hardware presence,
     * system permissions). Quick to compute and safe to call from any thread.
     *
     * If false, the provider will never be selected by the default
     * [DefaultPrivilegePolicy] and [PrivilegeHub] will not attempt to acquire via it.
     */
    val isAvailable: Boolean get() = true

    /**
     * Indicates whether this provider can grant the privilege immediately without acquisition.
     *
     * A provider guarantee: when true, calling [acquire] should succeed without external
     * setup (e.g., ADB pairing, user interaction). System privileges already granted via
     * system signature or module return true.
     *
     * Providers returning true are auto-selected by [DefaultPrivilegePolicy] when no
     * explicit preference is given, before falling back to priority-based resolution.
     */
    val isImmediate: Boolean get() = false

    /**
     * Attempts to acquire the privilege with an optional timeout.
     *
     * This is the main entry point for acquiring a privilege. It delegates to [onAcquire]
     * with timeout handling.
     *
     * @param timeoutMs Maximum time to wait for acquisition, in milliseconds. If null,
     *                  uses [DEFAULT_TIMEOUT_MS].
     * @return A [Result] containing the acquired privilege of type [T] on success, or an
     *         exception on failure or timeout.
     */
    suspend fun acquire(timeoutMs: Long = DEFAULT_TIMEOUT_MS): Result<T> {
        return try {
            withTimeout(timeoutMs) {
                onAcquire(timeoutMs)
            }
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("Privilege acquisition timed out after $timeoutMs ms", e))
        }
    }

    /**
     * Implementation method that providers override to provide the actual acquisition logic.
     *
     * Called by [acquire] to perform the actual work. Should NOT handle timeout logic -
     * that is managed by the [acquire] method.
     *
     * @param timeoutMs The timeout value passed to [acquire], for informational purposes
     *                  (e.g., to pass to lower-level APIs).
     * @return A [Result] indicating success or failure of the acquisition attempt.
     */
    suspend fun onAcquire(timeoutMs: Long): Result<T>
}
