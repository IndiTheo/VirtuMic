package com.indidevs.android.privilegehub

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * General state of the provision process.
 */
data class ProvisionState(
    val selectedId: String = PrivilegeProvider.ID_NONE,
    val status: PrivilegeStatus = PrivilegeStatus.NOT_SUPPLIED,
    val isAlive: Boolean = false,
    val handle: Privilege? = null
)

/**
 * Events for hub feedback.
 */
sealed class PrivilegeEvent {
    data class ProvisionError(val modeId: String, val throwable: Throwable) : PrivilegeEvent()
    data class PrivilegeProvided(val modeId: String) : PrivilegeEvent()
}

/**
 * Central orchestrator for privileges.
 */
object PrivilegeHub {
    private const val TAG = "PrivilegeHub"
    private val providers = ConcurrentHashMap<String, PrivilegeProvider<*>>()
    private val mutex = Mutex()
    private val hubScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(ProvisionState())
    val state: StateFlow<ProvisionState> = _state.asStateFlow()

    /**
     * A flow of the currently active privilege handle.
     */
    val activePrivilege: StateFlow<Privilege?> = _state
        .map { it.handle }
        .stateIn(hubScope, SharingStarted.Eagerly, null)

    private val _events = MutableSharedFlow<PrivilegeEvent>()
    val events: SharedFlow<PrivilegeEvent> = _events.asSharedFlow()

    /**
     * The strategy used to resolve the best privilege mode during initialization.
     */
    private var policy: PrivilegePolicy = DefaultPrivilegePolicy()

    /**
     * Updates the [PrivilegePolicy] used for mode resolution.
     * @param newPolicy The policy to use for resolving privilege modes.
     */
    fun setPolicy(newPolicy: PrivilegePolicy) {
        policy = newPolicy
    }

    private var provisionJob: Job? = null

    /**
     * Resolves and selects the best available privilege mode, respecting the [preferredId].
     * @param preferredId The preferred provider ID, or null to let the policy decide.
     * If the preferred ID is not registered, falls through to the [PrivilegePolicy] instead.
     */
    suspend fun initialize(preferredId: String? = null) {
        if (preferredId != null && providers.containsKey(preferredId)) {
            select(preferredId)
            return
        }

        // No explicit preference — let the policy decide (select, no auto-acquire).
        val resolved = resolve(preferredId)

        if (resolved != PrivilegeProvider.ID_NONE) {
            select(resolved)
        }
    }

    /**
     * Delegates to [policy.resolve]. Returns [PrivilegeProvider.ID_NONE] when no provider is suitable.
     */
    private fun resolve(preferredId: String?): String {
        return policy.resolve(preferredId, providers.values.toList())
    }

    /**
     * Unregisters a provider by ID. If it is the currently selected privilege, the active handle
     * is released and state is reset to [PrivilegeProvider.ID_NONE].
     *
     * @param id The ID of the provider to remove.
     * @return `true` if a provider was removed, `false` if no provider with that ID was registered.
     */
    suspend fun unregister(id: String): Boolean {
        val handleToRelease = mutex.withLock {
            val removed = providers.remove(id) != null
            if (!removed) return false

            var handleToRelease: Privilege? = null
            if (_state.value.selectedId == id) {
                provisionJob?.cancel()
                handleToRelease = _state.value.handle
                _state.update {
                    it.copy(
                        selectedId = PrivilegeProvider.ID_NONE,
                        status = PrivilegeStatus.NOT_SUPPLIED,
                        isAlive = false,
                        handle = null
                    )
                }
            }
            handleToRelease
        }

        if (handleToRelease != null) {
            Log.d(TAG, "Released previous privilege handle after unregister: $id")
            handleToRelease.release()
        }
        return true
    }

    /**
     * Unregisters all providers. Releases the active handle (if any) and resets selection to
     * @return The number of providers that were removed.
     */
    suspend fun unregisterAll(): Int {
        var removedCount = 0
        var activeId: String? = null
        val handleToRelease = mutex.withLock {
            removedCount = providers.size
            if (removedCount == 0) return@withLock null

            provisionJob?.cancel()
            activeId = _state.value.selectedId
            val handle = _state.value.handle
            providers.clear()
            _state.update {
                it.copy(
                    selectedId = PrivilegeProvider.ID_NONE,
                    status = PrivilegeStatus.NOT_SUPPLIED,
                    isAlive = false,
                    handle = null
                )
            }
            handle
        }

        if (handleToRelease != null) {
            Log.d(TAG, "Releasing previous privilege handle after unregisterAll: '$activeId'")
            handleToRelease.release()
        }
        return removedCount
    }

    /**
     * Registers one or more providers with the hub.
     * @param list Variable number of provider instances to register.
     * @throws IllegalArgumentException if any provider's id is [PrivilegeProvider.ID_NONE].
     */
    fun register(vararg list: PrivilegeProvider<*>) {
        list.forEach {
            require(it.id != PrivilegeProvider.ID_NONE) {
                "Cannot register provider with id '${PrivilegeProvider.ID_NONE}': reserved"
            }
            providers[it.id] = it
            Log.i(TAG, "Registered provider: ${it.id} (${it.name})")
        }
    }

    /**
     * @return A snapshot of all currently registered providers as read-only views.
     */
    fun getProviders(): List<PrivilegeProviderView> = providers.values.map { it.toView() }

    /**
     * Selects a privilege mode by ID. Automatically releases the previous one.
     * @param id The ID of the provider to select. Use [ID_NONE] to clear the current selection.
     */
    suspend fun select(id: String) {
        val previousId = _state.value.selectedId
        Log.i(TAG, "Selecting privilege mode: $id (previous: $previousId)")

        val old = mutex.withLock {
            if (previousId == id) return@withLock null
            provisionJob?.cancel()

            // Release previous
            val handle = _state.value.handle
            _state.update {
                it.copy(
                    selectedId = id,
                    status = PrivilegeStatus.NOT_SUPPLIED,
                    isAlive = false,
                    handle = null
                )
            }
            handle
        }

        if (old != null) {
            Log.d(TAG, "Releasing previous privilege handle for '$previousId'")
            old.release()
        }
    }

    /**
     * Triggers the provision of the currently selected privilege.
     * Returns a generic [Privilege] result for pattern matching.
     * @param timeoutMs Optional timeout in milliseconds. If null, uses the provider’s default timeout.
     * @param force If true, cancels any ongoing acquisition job and forces a new attempt.
     * @return A [Result] containing the acquired [Privilege] on success, or an exception on failure/cancellation.
     */
    suspend fun provide(timeoutMs: Long? = null, force: Boolean = false): Result<Privilege> {
        val job = mutex.withLock {
            val acquiring = _state.value.status == PrivilegeStatus.ACQUIRING
            if (!force && acquiring && provisionJob?.isActive == true) {
                Log.d(
                    TAG,
                    "Acquisition already in progress for mode " +
                        "'${_state.value.selectedId}'. Joining active job."
                )
                return@withLock provisionJob!!
            }

            provisionJob?.cancelAndJoin()
            val job = hubScope.launch {
                provideInternal(timeoutMs, force)
            }
            provisionJob = job
            job
        }

        job.join()

        // Return current active privilege if provided
        val active = _state.value.handle
        return if (_state.value.status == PrivilegeStatus.PROVIDED && active != null) {
            Result.success(active)
        } else {
            Result.failure(Exception("Provision failed or cancelled"))
        }
    }

    private suspend fun provideInternal(
        timeoutMs: Long? = null,
        force: Boolean = false
    ): Result<Privilege> {
        val (provider, initialId, oldHandle) = mutex.withLock {
            val id = _state.value.selectedId
            val p = providers[id] ?: run {
                Log.e(TAG, "Provision failed: No provider registered for ID '$id'")
                return Result.failure(Exception("No provider for $id"))
            }

            // Check if already provided (unless force re-acquisition is requested)
            if (!force &&
                _state.value.status == PrivilegeStatus.PROVIDED &&
                _state.value.handle != null
            ) {
                Log.d(TAG, "Privilege '$id' already provided and active")
                return Result.success(_state.value.handle!!)
            }

            Log.i(TAG, "Acquiring privilege: $id...")
            val old = _state.value.handle
            _state.update { it.copy(status = PrivilegeStatus.ACQUIRING, handle = null) }
            Triple(p, id, old)
        }

        oldHandle?.release()

        val result = if (timeoutMs != null) {
            provider.acquire(timeoutMs)
        } else {
            provider.acquire()
        }

        // Update final state
        return mutex.withLock {
            // Check if we switched modes while acquiring
            if (_state.value.selectedId != initialId) {
                result.onSuccess { it.release() } // Cleanup the leaked acquisition
                return Result.failure(Exception("Mode changed during acquisition"))
            }

            result.onSuccess { privilege ->
                Log.i(TAG, "Successfully acquired privilege: $initialId")
                _state.update {
                    it.copy(
                        status = PrivilegeStatus.PROVIDED,
                        isAlive = true,
                        handle = privilege
                    )
                }
                _events.emit(PrivilegeEvent.PrivilegeProvided(initialId))
            }.onFailure { error ->
                Log.e(TAG, "Failed to acquire privilege: $initialId", error)
                _state.update { it.copy(status = PrivilegeStatus.NOT_SUPPLIED) }
                _events.emit(PrivilegeEvent.ProvisionError(initialId, error))
            }
            result
        }
    }

    /**
     * Releases the active privilege, if any.
     */
    suspend fun release() {
        Log.i(TAG, "Releasing active privilege: ${_state.value.selectedId}")
        val old = mutex.withLock {
            provisionJob?.cancelAndJoin() // Wait for provision to stop
            val handle = _state.value.handle
            _state.update {
                it.copy(
                    status = PrivilegeStatus.NOT_SUPPLIED,
                    isAlive = false,
                    handle = null
                )
            }
            handle
        }
        old?.release()
    }

    /**
     * Returns the active handle cast to [T], or null if it's the wrong type or not provided.
     * @return The active handle as type [T], or null.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Privilege> getHandleOrNull(): T? = _state.value.handle as? T

    /**
     * Checks whether the currently active privilege (if any) is still alive/valid.
     * @return True if a privilege is active and its underlying resource is still valid; false otherwise.
     */
    suspend fun checkHealth(): Boolean = mutex.withLock {
        val active = _state.value.handle ?: return false
        val alive = active.isAlive()
        if (!alive) {
            Log.w(
                TAG,
                "Privilege '${_state.value.selectedId}' health check failed - no longer alive"
            )
        }
        _state.update { it.copy(isAlive = alive) }
        alive
    }
}

/**
 * Strategy for deciding which privilege mode to use.
 */
interface PrivilegePolicy {
    /**
     * Determines which provider ID should be used.
     * @param preferredId The ID the caller would like to use, or null to indicate
     *                    that the policy should choose freely.
     * @param providers   An iterable of all currently registered providers.
     * @return The ID of the provider to select, or [ID_NONE] if no provider is suitable.
     */
    fun resolve(preferredId: String?, providers: Iterable<PrivilegeProvider<*>>): String
}

/**
 * The default resolution policy:
 * 1. Returns the preferredId if it is valid and available.
 * 2. Auto-selects any provider that's immediately available (isImmediate == true), e.g. system privileges already granted.
 * 3. Otherwise, returns the highest priority available mode.
 */
class DefaultPrivilegePolicy : PrivilegePolicy {
    override fun resolve(preferredId: String?, providers: Iterable<PrivilegeProvider<*>>): String {
        val available = providers.filter { it.isAvailable }.toList()

        // Explicit preference: use it if available and registered.
        if (preferredId != null && preferredId != PrivilegeProvider.ID_NONE) {
            val preferred = available.find { it.id == preferredId }
            if (preferred != null) return preferred.id
            // Stale/unknown preference, fall through to let the caller decide
            return PrivilegeProvider.ID_NONE
        }

        // No explicit preference: isImmediate providers are auto-selected AND auto-acquired.
        val immediate = available.firstOrNull { it.isImmediate }
        if (immediate != null) return immediate.id

        // Otherwise, mark the highest-priority available provider (no acquire).
        return available.maxByOrNull { it.priority }?.id ?: PrivilegeProvider.ID_NONE
    }
}
