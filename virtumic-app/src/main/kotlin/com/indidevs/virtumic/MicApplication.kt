package com.indidevs.virtumic

import android.app.Application
import com.indidevs.android.privilegehub.PrivilegeHub
import com.indidevs.android.privilegehub.PrivilegeStatus
import com.indidevs.virtumic.core.VMicEngine
import com.indidevs.virtumic.data.repository.DefaultSettingsRepository
import com.indidevs.virtumic.dependencies.VMicAppContainer
import com.indidevs.virtumic.service.integration.VMicEngineProvider
import java.security.Security
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.bouncycastle.jce.provider.BouncyCastleProvider

class MicApplication : Application(), VMicEngineProvider {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val appContainer by lazy { VMicAppContainer(this, applicationScope) }

    @Volatile
    override var engine: VMicEngine? = null
        private set

    override fun onCreate() {
        // Initialize BouncyCastle for ADB TLS support
        Security.addProvider(BouncyCastleProvider())

        super.onCreate()

        // Register privilege providers
        PrivilegeHub.register(
            appContainer.adbPrivilegeProvider,
            appContainer.shizukuPrivilegeProvider,
            appContainer.systemPrivilegeProvider
        )

        // Sync initial privilege mode from settings and detect system module
        applicationScope.launch {
            val settings = DefaultSettingsRepository(this@MicApplication)
            PrivilegeHub.initialize(settings.getPrivilegeMode())
        }

        // Manage engine lifecycle based on privilege status
        applicationScope.launch {
            PrivilegeHub.state.collect { state ->
                if (state.status == PrivilegeStatus.PROVIDED && state.isAlive) {
                    if (engine == null) {
                        engine = VMicEngine(this@MicApplication)
                    }
                } else {
                    engine = null
                }
            }
        }
    }
}
