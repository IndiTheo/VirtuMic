package com.indidevs.android.privilegehub.providers

import android.content.Context
import android.util.Log
import com.indidevs.android.adbeesh.config.AdbSessionConfig
import com.indidevs.android.adbeesh.notifications.NotificationCallback
import com.indidevs.android.adbeesh.notifications.NotificationCodeProvider
import com.indidevs.android.adbeesh.notifications.NotificationFlowConfig
import com.indidevs.android.adbeesh.pairing.AdbPairing
import com.indidevs.android.adbeesh.pairing.AdbPairingStatus
import com.indidevs.android.adbeesh.pairing.AdbPairingWorkflow
import com.indidevs.android.adbeesh.runtime.AdbSession
import com.indidevs.android.privilegehub.HintId
import com.indidevs.android.privilegehub.Privilege
import com.indidevs.android.privilegehub.PrivilegeHint
import com.indidevs.android.privilegehub.PrivilegeIds
import com.indidevs.android.privilegehub.PrivilegeProvider
import com.indidevs.android.shellom.InstrumentationShellProvider
import com.indidevs.android.shellom.ShellStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * A privilege handle for ADB-based process elevation.
 *
 * This provider elevates the calling app's process. Once elevated, the process holds
 * the requested permissions permanently for its lifetime.
 * The ADB session is used ONLY as a transport to trigger elevation
 * and is CLOSED immediately after success.
 *
 * This handle does NOT provide a usable ADB session for arbitrary shell commands.
 * If raw ADB access is needed, use [AdbPairing.openSession()] directly.
 *
 * Future versions may expose an ADB session handle if a use case arises.
 */
class AdbPrivilege(
    private val onRelease: suspend () -> Unit
) : Privilege {
    override suspend fun release() = onRelease()

    override suspend fun isAlive(): Boolean = true
}

/**
 * A generic provider that uses Wireless ADB and shellom to acquire privileges.
 */
class AdbPrivilegeProvider(
    private val context: Context,
    private val pairingId: String,
    private val sessionConfig: AdbSessionConfig,
    private val requiredPermissions: List<String>,
    override val priority: Int,
    private val scope: CoroutineScope
) : PrivilegeProvider<AdbPrivilege> {
    override val id: String = PrivilegeIds.ADB_ELEVATION
    override val name: String = "Wireless ADB"

    private enum class ProvisionStatus { IDLE, SEARCHING, PAIRED, ELEVATED }

    enum class AdbHint(override val displayName: String) : HintId {
        ELEVATED("Elevated"),
        PAIRED("Paired"),
        SEARCHING("Searching"),
        DISCONNECTED("Disconnected"),
        ERROR("Pairing Error"),
        NOT_PAIRED("Not Paired")
    }

    private val pairing = AdbPairing.get(context.applicationContext, pairingId, sessionConfig)
    private val statusFlow = MutableStateFlow(ProvisionStatus.IDLE)
    private val provisionErrorFlow = MutableStateFlow<Throwable?>(null)
    private val isStickyElevatedFlow = MutableStateFlow(false)
    private val isLastPingSuccessFlow = MutableStateFlow(true)

    override val hints: StateFlow<Set<PrivilegeHint>> = combine(
        pairing.status,
        statusFlow,
        provisionErrorFlow,
        isStickyElevatedFlow,
        isLastPingSuccessFlow
    ) {
            pairingStatus: AdbPairingStatus,
            provisionStatus: ProvisionStatus,
            provisionError: Throwable?,
            isStickyElevated: Boolean,
            isLastPingSuccess: Boolean ->
        buildSet {
            if (provisionError != null) {
                add(PrivilegeHint.error(AdbHint.ERROR))
            }
            // Identity: Always show if the process is elevated
            if (isStickyElevated || provisionStatus == ProvisionStatus.ELEVATED) {
                add(PrivilegeHint.success(AdbHint.ELEVATED))
            }

            // Pairing status
            if (pairingStatus == AdbPairingStatus.PAIRED) {
                add(PrivilegeHint.info(AdbHint.PAIRED))
                if (!isLastPingSuccess) {
                    add(PrivilegeHint.error(AdbHint.DISCONNECTED))
                }
            }

            // Ability to elevate
            when (provisionStatus) {
                ProvisionStatus.SEARCHING -> add(PrivilegeHint.info(AdbHint.SEARCHING))
                ProvisionStatus.IDLE, ProvisionStatus.ELEVATED -> {
                    if (pairingStatus == AdbPairingStatus.ERROR) {
                        add(PrivilegeHint.error(AdbHint.ERROR))
                    } else if (pairingStatus != AdbPairingStatus.PAIRED) {
                        add(PrivilegeHint.info(AdbHint.NOT_PAIRED))
                    }
                }
                else -> {}
            }
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptySet())

    override suspend fun onAcquire(timeoutMs: Long): Result<AdbPrivilege> =
        withContext(Dispatchers.IO) {
            isLastPingSuccessFlow.value = true
            val appContext = context.applicationContext

            // If we are already elevated, we have enough to function.
            if (isStickyElevatedFlow.value) {
                Log.i(TAG, "Already elevated (sticky). Returning early success.")
                statusFlow.value = ProvisionStatus.ELEVATED

                // Check if session can be opened to update hints
                scope.launch {
                    runCatching {
                        pairing.openSession(1000).close()
                        isLastPingSuccessFlow.value = true
                    }.onFailure {
                        // Only mark as disconnected if it was already paired and the ping failed
                        if (pairing.status.value == AdbPairingStatus.PAIRED) {
                            isLastPingSuccessFlow.value = false
                        }
                    }
                }
                return@withContext Result.success(
                    AdbPrivilege({ statusFlow.value = ProvisionStatus.IDLE })
                )
            }

            try {
                Log.d(TAG, "Starting ADB provision flow for pairing ID: $pairingId")

                val workflow = AdbPairingWorkflow.Builder()
                    .withCodeProvider(
                        NotificationCodeProvider(
                            appContext,
                            NotificationFlowConfig.Builder().build()
                        )
                    )
                    .addCallback(
                        NotificationCallback(appContext, NotificationFlowConfig.Builder().build())
                    )
                    .build()

                coroutineScope {
                    val pairingJob = launch {
                        Log.d(TAG, "Pairing status is: ${pairing.status.value}")
                        if (pairing.status.value != AdbPairingStatus.PAIRED) {
                            Log.d(TAG, "Going to pair ADB")
                            try {
                                pairing.ensurePaired(workflow)
                                Log.d(TAG, "ADB pairing completed.")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error while pairing ADB", e)
                                throw e
                            }
                        } else {
                            Log.d(TAG, "ADB already paired, skipping")
                        }
                    }

                    var currentSession: AdbSession? = null
                    var currentElevationJob: Job? = null

                    try {
                        pairingJob.join()

                        val session = pairing.openSession(timeoutMs = timeoutMs)
                        currentSession = session
                        Log.d(TAG, "ADB Connection complete. Triggering elevation...")
                        statusFlow.value = ProvisionStatus.PAIRED

                        val elevationJob = Job()
                        currentElevationJob = elevationJob
                        val elevationScope = CoroutineScope(elevationJob + Dispatchers.IO)

                        val executor = KadbExecutor(
                            session = session,
                            elevationJob = elevationJob,
                            isElevated = { isStickyElevatedFlow.value }
                        )

                        val shellProvider = InstrumentationShellProvider(
                            appContext,
                            elevationScope,
                            executor,
                            requiredPermissions
                        )

                        elevationScope.launch {
                            shellProvider.status.collect { status ->
                                if (status == ShellStatus.READY) {
                                    statusFlow.value = ProvisionStatus.ELEVATED
                                    isStickyElevatedFlow.value = true
                                }
                            }
                        }

                        withTimeout(timeoutMs) { shellProvider.awaitContext() }
                        Log.i(TAG, "Process elevated successfully")

                        pairingJob.cancel()
                        session.close()
                        provisionErrorFlow.value = null

                        Result.success(
                            AdbPrivilege({
                                elevationJob.cancelAndJoin()
                                statusFlow.value = ProvisionStatus.IDLE
                                provisionErrorFlow.value = null
                            })
                        )
                    } catch (e: Exception) {
                        statusFlow.value = ProvisionStatus.IDLE
                        pairingJob.cancel()
                        currentElevationJob?.cancel()
                        currentSession?.close()
                        pairing.close()
                        if (e is kotlinx.coroutines.CancellationException) {
                            throw e
                        }
                        provisionErrorFlow.value = e
                        if (isStickyElevatedFlow.value) {
                            Log.i(
                                TAG,
                                "Acquisition failed but process is already elevated. Returning degraded success."
                            )
                            Result.success(
                                AdbPrivilege({
                                    statusFlow.value = ProvisionStatus.IDLE
                                })
                            )
                        } else {
                            throw e
                        }
                    }
                }
            } catch (e: Exception) {
                statusFlow.value = ProvisionStatus.IDLE
                if (e is kotlinx.coroutines.CancellationException) {
                    throw e
                }
                Log.e(TAG, "ADB provision failed", e)
                provisionErrorFlow.value = e
                if (isStickyElevatedFlow.value) {
                    Result.success(
                        AdbPrivilege({
                            statusFlow.value = ProvisionStatus.IDLE
                            provisionErrorFlow.value = null
                        })
                    )
                } else {
                    Result.failure(e)
                }
            }
        }

    companion object {
        private const val TAG = "AdbPrivilegeProvider"
    }
}
