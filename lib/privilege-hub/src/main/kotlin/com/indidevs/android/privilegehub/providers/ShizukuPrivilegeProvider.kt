package com.indidevs.android.privilegehub.providers

import android.content.Context
import android.util.Log
import com.indidevs.android.privilegehub.HintId
import com.indidevs.android.privilegehub.Privilege
import com.indidevs.android.privilegehub.PrivilegeHint
import com.indidevs.android.privilegehub.PrivilegeIds
import com.indidevs.android.privilegehub.PrivilegeProvider
import com.indidevs.android.shellom.InstrumentationShellProvider
import com.indidevs.android.shellom.ShellExecutor
import com.indidevs.android.shellom.ShellProvider
import com.indidevs.android.shellom.ShellStatus
import com.indidevs.android.shellom.integrations.shizuku.ShizukuExecutor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * A privilege handle for Shizuku-based process elevation.
 */
class ShizukuPrivilege(
    private val onRelease: suspend () -> Unit
) : Privilege {
    override suspend fun release() = onRelease()

    override suspend fun isAlive(): Boolean = true
}

/**
 * A generic provider that uses Shizuku and shellom to acquire privileges.
 */
class ShizukuPrivilegeProvider(
    private val context: Context,
    private val requiredPermissions: List<String>,
    override val priority: Int,
    private val scope: CoroutineScope,
    private val executor: ShellExecutor = ShizukuExecutor(autoRequestPermission = true),
    /**
     * Creates the [ShellProvider] used to drive elevation. Overridable so tests can
     * substitute a fake and exercise the success/failure routes without a real
     * instrumentation process.
     *
     * The scope handed over is the acquisition's own scope: the elevation is a
     * one-shot command that finishes with the acquisition.
     */
    private val shellProviderFactory: (
        CoroutineScope,
        ShellExecutor
    ) -> ShellProvider = { elevationScope, exec ->
        InstrumentationShellProvider(context, elevationScope, exec, requiredPermissions)
    },
    /**
     * The dispatcher the acquisition runs on. Tests inject a test dispatcher so the
     * acquisition and the timeout that guards it share one clock.
     */
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : PrivilegeProvider<ShizukuPrivilege> {
    override val id: String = PrivilegeIds.SHIZUKU_ELEVATION
    override val name: String = "Shizuku"

    /**
     * Shizuku is a valid option even when its service is not running: the user can
     * start it and grant the permission from the picker.
     */
    override val isAvailable: Boolean
        get() = true

    /**
     * Shizuku can deliver right now when its service is running and this app already
     * holds the Shizuku permission.
     */
    override val isImmediate: Boolean
        get() {
            val shizukuExec = executor as? ShizukuExecutor ?: return false
            return shizukuExec.isAvailable() && shizukuExec.hasPermission()
        }

    private enum class ProvisionStatus { IDLE, ELEVATED }

    enum class ShizukuHint(override val displayName: String) : HintId {
        ELEVATED("Elevated"),
        AVAILABLE("Service bound"),
        PERMISSION_GRANTED("Permission granted"),
        PERMISSION_DENIED("Permission denied"),
        NOT_CONNECTED("Shizuku not running"),
        ERROR("Shizuku error")
    }

    private val statusFlow = MutableStateFlow(ProvisionStatus.IDLE)
    private val provisionErrorFlow = MutableStateFlow<Throwable?>(null)
    private val isStickyElevatedFlow = MutableStateFlow(false)

    /**
     * The [scope] is long-lived: it backs the [hints] StateFlow via `stateIn` and is
     * reused across acquisitions. It is owned by the caller (the application scope), so
     * the acquisition never runs in it and never cancels it.
     */
    override val hints: StateFlow<Set<PrivilegeHint>> = combine(
        statusFlow,
        provisionErrorFlow,
        isStickyElevatedFlow
    ) { provisionStatus: ProvisionStatus, provisionError: Throwable?, isStickyElevated: Boolean ->
        buildSet {
            if (provisionError != null) {
                add(PrivilegeHint.error(ShizukuHint.ERROR))
            }
            if (isStickyElevated || provisionStatus == ProvisionStatus.ELEVATED) {
                add(PrivilegeHint.success(ShizukuHint.ELEVATED))
            }

            val shizukuExec = executor as? ShizukuExecutor
            val isAvail = shizukuExec?.isAvailable() ?: false
            val hasPerm = shizukuExec?.hasPermission() ?: false
            if (isAvail) {
                add(PrivilegeHint.info(ShizukuHint.AVAILABLE))
                if (hasPerm) {
                    add(PrivilegeHint.info(ShizukuHint.PERMISSION_GRANTED))
                } else {
                    add(PrivilegeHint.warning(ShizukuHint.PERMISSION_DENIED))
                }
            } else {
                add(PrivilegeHint.error(ShizukuHint.NOT_CONNECTED))
            }
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptySet())

    override suspend fun onAcquire(timeoutMs: Long): Result<ShizukuPrivilege> =
        withContext(dispatcher) {
            if (isStickyElevatedFlow.value) {
                Log.i(TAG, "Already elevated (sticky). Returning early success.")
                statusFlow.value = ProvisionStatus.ELEVATED
                return@withContext Result.success(
                    ShizukuPrivilege({ statusFlow.value = ProvisionStatus.IDLE })
                )
            }

            try {
                Log.d(TAG, "Starting Shizuku provision flow...")

                try {
                    val shellProvider = shellProviderFactory(this, executor)

                    withTimeout(timeoutMs) { shellProvider.awaitContext() }
                    Log.i(TAG, "Process elevated successfully via Shizuku")

                    if (shellProvider.status.value == ShellStatus.READY) {
                        statusFlow.value = ProvisionStatus.ELEVATED
                        isStickyElevatedFlow.value = true
                    }

                    provisionErrorFlow.value = null

                    Result.success(
                        ShizukuPrivilege({
                            statusFlow.value = ProvisionStatus.IDLE
                            provisionErrorFlow.value = null
                        })
                    )
                } catch (e: Exception) {
                    statusFlow.value = ProvisionStatus.IDLE
                    if (e is kotlinx.coroutines.CancellationException) {
                        throw e
                    }
                    provisionErrorFlow.value = e
                    throw e
                }
            } catch (e: Exception) {
                statusFlow.value = ProvisionStatus.IDLE
                if (e is kotlinx.coroutines.CancellationException) {
                    throw e
                }
                Log.e(TAG, "Shizuku provision failed", e)
                provisionErrorFlow.value = e
                Result.failure(e)
            }
        }

    companion object {
        private const val TAG = "ShizukuPrivilegeProvider"
    }
}
