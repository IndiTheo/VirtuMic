package com.indidevs.virtumic.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.indidevs.android.privilegehub.PrivilegeHub
import com.indidevs.android.privilegehub.PrivilegeProvider
import com.indidevs.virtumic.MicApplication
import com.indidevs.virtumic.data.repository.DefaultSettingsRepository
import com.indidevs.virtumic.dependencies.VMicAppContainer
import com.indidevs.virtumic.domain.repository.NotificationCustomizationConfig
import com.indidevs.virtumic.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val privilegeMode: String = PrivilegeProvider.ID_NONE,
    val privilegeDisplayName: String = PrivilegeProvider.ID_NONE,
    val showPrivilegePicker: Boolean = false,

    // Audio Pipeline State
    val isMatchSampleRateEnabled: Boolean = true,
    val isMatchChannelsEnabled: Boolean = true,
    val sampleRate: Int = SettingsRepository.DEFAULT_SAMPLE_RATE,
    val channelMask: Int = SettingsRepository.DEFAULT_CHANNEL_MASK,
    val capturePresets: IntArray = intArrayOf(),
    val showFormatPicker: Boolean = false,

    // Customization State
    val notificationConfig: NotificationCustomizationConfig = NotificationCustomizationConfig(),
    val showNotificationCustomization: Boolean = false
)

class SettingsViewModel(
    private val appContainer: VMicAppContainer,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val providerNames = PrivilegeHub.getProviders().associate { it.id to it.name }

    /** Resolve display name for a provider id, falling back to "Not selected". */
    private fun displayNameFor(providerId: String): String =
        providerNames[providerId] ?: "Not selected"

    init {
        // Load initial state
        viewModelScope.launch {
            val format = settingsRepository.getPreferredFormat()
            val initialMode = settingsRepository.getPrivilegeMode()
            val notifConfig = settingsRepository.getNotificationCustomization()
            _uiState.update {
                it.copy(
                    privilegeMode = initialMode,
                    privilegeDisplayName = displayNameFor(initialMode),
                    isMatchSampleRateEnabled = settingsRepository.isMatchSampleRateEnabled(),
                    isMatchChannelsEnabled = settingsRepository.isMatchChannelsEnabled(),
                    sampleRate = format.sampleRate,
                    channelMask = format.channelMask,
                    capturePresets = settingsRepository.getPreferredCapturePresets(),
                    notificationConfig = notifConfig
                )
            }
        }

        // Observe Hub to update UI
        viewModelScope.launch {
            PrivilegeHub.state.collect { state ->
                _uiState.update {
                    it.copy(
                        privilegeMode = state.selectedId,
                        privilegeDisplayName = displayNameFor(state.selectedId)
                    )
                }
            }
        }
    }

    fun setMatchSampleRateEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setMatchSampleRateEnabled(enabled)
            _uiState.update {
                it.copy(
                    isMatchSampleRateEnabled = enabled
                )
            }
        }
    }

    fun setMatchChannelsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setMatchChannelsEnabled(enabled)
            _uiState.update {
                it.copy(
                    isMatchChannelsEnabled = enabled
                )
            }
        }
    }

    fun updateFormat(sampleRate: Int, channelMask: Int) {
        viewModelScope.launch {
            settingsRepository.setPreferredFormat(sampleRate, channelMask)
            _uiState.update {
                it.copy(
                    sampleRate = sampleRate,
                    channelMask = channelMask,
                    showFormatPicker = false
                )
            }
        }
    }

    fun onFormatSettingsClick() {
        _uiState.update { it.copy(showFormatPicker = true) }
    }

    fun dismissFormatPicker() {
        _uiState.update { it.copy(showFormatPicker = false) }
    }

    fun onCustomizationClick() {
        _uiState.update { it.copy(showNotificationCustomization = true) }
    }

    fun dismissCustomization() {
        _uiState.update { it.copy(showNotificationCustomization = false) }
    }

    fun saveCustomization(config: NotificationCustomizationConfig) {
        viewModelScope.launch {
            settingsRepository.setNotificationCustomization(config)
            _uiState.update {
                it.copy(
                    notificationConfig = config,
                    showNotificationCustomization = false
                )
            }
        }
    }

    fun onPrivilegeProviderClick() {
        _uiState.update { it.copy(showPrivilegePicker = true) }
    }

    fun onPrivilegeModeSelected(modeId: String) {
        viewModelScope.launch {
            settingsRepository.setPrivilegeMode(modeId)
            PrivilegeHub.select(modeId)
            launch { PrivilegeHub.provide() } // Don't block UI dismissal on provision
            _uiState.update { it.copy(showPrivilegePicker = false) }
        }
    }

    fun dismissPrivilegePicker() {
        _uiState.update { it.copy(showPrivilegePicker = false) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY]) as MicApplication
                val container = application.appContainer
                SettingsViewModel(
                    container,
                    DefaultSettingsRepository(container.context)
                )
            }
        }
    }
}
