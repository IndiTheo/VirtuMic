package com.indidevs.virtumic.dependencies

import android.content.Context
import com.indidevs.android.adbeesh.config.AdbSessionConfig
import com.indidevs.android.privilegehub.providers.AdbPrivilegeProvider
import com.indidevs.android.privilegehub.providers.ShizukuPrivilegeProvider
import com.indidevs.android.privilegehub.providers.SystemPrivilegeProvider
import com.indidevs.virtumic.service.session.VMicMediaSessionService
import kotlinx.coroutines.CoroutineScope

class VMicAppContainer(
    val context: Context,
    val scope: CoroutineScope
) {
    private val requiredPermissions = listOf(
        "android.permission.MODIFY_AUDIO_ROUTING",
        "android.permission.CAPTURE_AUDIO_OUTPUT"
    )

    val adbPrivilegeProvider: AdbPrivilegeProvider by lazy {
        AdbPrivilegeProvider(
            context = context,
            pairingId = "virtumic_app",
            sessionConfig = AdbSessionConfig.Builder()
                .identityName("VirtuMic")
                .build(),
            requiredPermissions = requiredPermissions,
            priority = 50,
            scope = scope
        )
    }

    val shizukuPrivilegeProvider: ShizukuPrivilegeProvider by lazy {
        ShizukuPrivilegeProvider(
            context = context,
            requiredPermissions = requiredPermissions,
            priority = 75,
            scope = scope
        )
    }

    val systemPrivilegeProvider by lazy {
        SystemPrivilegeProvider(
            context = context,
            requiredPermissions = requiredPermissions,
            priority = 100
        )
    }

    val mediaSessionServiceClass: Class<*> = VMicMediaSessionService::class.java
}
