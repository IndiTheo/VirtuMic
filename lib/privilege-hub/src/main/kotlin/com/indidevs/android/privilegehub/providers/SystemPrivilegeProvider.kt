package com.indidevs.android.privilegehub.providers

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.indidevs.android.privilegehub.HintId
import com.indidevs.android.privilegehub.Privilege
import com.indidevs.android.privilegehub.PrivilegeHint
import com.indidevs.android.privilegehub.PrivilegeIds
import com.indidevs.android.privilegehub.PrivilegeProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A handle for system privileges that require no cleanup.
 */
object SystemPrivilege : Privilege {
    override suspend fun release() {}
    override suspend fun isAlive(): Boolean = true
}

/**
 * A generic provider that detects if the app already has system privileges
 * (e.g., via a systemless module or system-signed app).
 */
class SystemPrivilegeProvider(
    private val context: Context,
    private val requiredPermissions: List<String>,
    override val priority: Int
) : PrivilegeProvider<SystemPrivilege> {
    override val id: String = PrivilegeIds.SYSTEM
    override val name: String = "System"

    enum class SystemHint(override val displayName: String) : HintId {
        GRANTED("Granted by System"),
        NOT_DETECTED("Not detected")
    }

    override val hints: StateFlow<Set<PrivilegeHint>> = MutableStateFlow(
        if (isPrivileged()) {
            setOf(PrivilegeHint.success(SystemHint.GRANTED))
        } else {
            setOf(PrivilegeHint.error(SystemHint.NOT_DETECTED))
        }
    ).asStateFlow()

    override val isAvailable: Boolean = isPrivileged()
    override val isImmediate: Boolean get() = isPrivileged()

    override suspend fun onAcquire(timeoutMs: Long): Result<SystemPrivilege> {
        val privileged = isPrivileged()
        Log.d("SystemPrivilege", "Requesting system privilege. isPrivileged: $privileged")

        return if (privileged) {
            Result.success(SystemPrivilege)
        } else {
            Result.failure(Exception("System privileges not detected."))
        }
    }

    fun isPrivileged(): Boolean {
        val pm = context.packageManager
        val packageName = context.packageName
        return requiredPermissions.all { perm ->
            pm.checkPermission(perm, packageName) == PackageManager.PERMISSION_GRANTED
        }
    }
}
