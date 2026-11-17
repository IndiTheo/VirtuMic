package com.indidevs.virtumic.ui.feature.start

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.indidevs.android.privilegehub.PrivilegeEvent
import com.indidevs.android.privilegehub.PrivilegeHub
import com.indidevs.android.privilegehub.PrivilegeProvider
import com.indidevs.android.privilegehub.PrivilegeStatus
import com.indidevs.virtumic.MicApplication
import com.indidevs.virtumic.data.repository.DefaultAppRepository
import com.indidevs.virtumic.data.repository.DefaultAudioMetadataRepository
import com.indidevs.virtumic.data.repository.DefaultSettingsRepository
import com.indidevs.virtumic.dependencies.VMicAppContainer
import com.indidevs.virtumic.domain.model.SourceType
import com.indidevs.virtumic.domain.model.StreamingPrivilege
import com.indidevs.virtumic.domain.repository.AppRepository
import com.indidevs.virtumic.domain.repository.AudioMetadataRepository
import com.indidevs.virtumic.domain.repository.SettingsRepository
import com.indidevs.virtumic.streaming.StreamingJob
import com.indidevs.virtumic.ui.model.AppItem
import com.indidevs.virtumic.ui.model.toAppSourceInfo
import kotlin.Triple
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StartViewModel(
    private val appContainer: VMicAppContainer,
    private val appRepository: AppRepository,
    private val audioMetadataRepository: AudioMetadataRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    companion object {
        private const val TAG = "StartViewModel"
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY]) as MicApplication
                val container = application.appContainer
                StartViewModel(
                    container,
                    DefaultAppRepository(container.context),
                    DefaultAudioMetadataRepository(container.context),
                    DefaultSettingsRepository(container.context)
                )
            }
        }
    }

    private val _uiState = MutableStateFlow(StartUiState())
    val uiState: StateFlow<StartUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var currentStreamingJob: StreamingJob? = null

    init {
        // Observe privilege hub state to allow streaming only when privileged mode is set.
        // We use Triple + distinctUntilChanged to ensure we only update UI when business-critical state changes.
        viewModelScope.launch {
            PrivilegeHub.state
                .map { Triple(it.selectedId, it.isAlive, it.status) }
                .distinctUntilChanged()
                .collect { (selectedId, isAlive, status) ->
                    val privilegeStatus = when (status) {
                        PrivilegeStatus.NOT_SUPPLIED -> StreamingPrivilege.NOT_READY
                        PrivilegeStatus.ACQUIRING -> StreamingPrivilege.STARTING
                        PrivilegeStatus.PROVIDED ->
                            if (isAlive) {
                                StreamingPrivilege.READY
                            } else {
                                StreamingPrivilege.NOT_READY
                            }
                    }
                    _uiState.update {
                        it.copy(
                            privilegeMode = selectedId,
                            privilegeStatus = privilegeStatus
                        )
                    }

                    if (selectedId == PrivilegeProvider.ID_NONE) {
                        _uiState.update { it.copy(showPrivilegePicker = true) }
                    }
                }
        }

        // Observe privilege events for UI feedback (Toasts)
        viewModelScope.launch {
            PrivilegeHub.events.collect { event ->
                when (event) {
                    is PrivilegeEvent.ProvisionError -> {
                        val message = "Failed to get ${event.modeId}:" +
                            (event.throwable.message ?: "Unknown error")
                        Toast.makeText(appContainer.context, message, Toast.LENGTH_LONG).show()
                    }
                    is PrivilegeEvent.PrivilegeProvided -> {
                    }
                }
            }
        }

        // Show privilege picker on first launch if no provider selected yet
        viewModelScope.launch {
            val state = PrivilegeHub.state.value
            if (state.selectedId == PrivilegeProvider.ID_NONE) {
                _uiState.update { it.copy(showPrivilegePicker = true) }
            }
        }

        // Initial settings sync
        viewModelScope.launch {
            val savedType = settingsRepository.getSelectedSourceType()
            _uiState.update {
                it.setSourceType(
                    savedType
                ).withAutoStartEnabled(settingsRepository.isAutoStartEnabled())
            }

            val savedFileUri = settingsRepository.getSelectedFileUri()
            savedFileUri?.let { uri ->
                viewModelScope.launch {
                    val info = audioMetadataRepository.getFileSourceInfo(uri)
                    _uiState.update { it.withFileInfo(info) }
                }
            }

            val savedAppInfo = settingsRepository.getSelectedAppInfo()
            savedAppInfo?.let { info ->
                _uiState.update { it.withAppInfo(info) }
            }
        }
    }

    fun setSourceType(type: SourceType) {
        _uiState.update { it.setSourceType(type) }
        viewModelScope.launch {
            settingsRepository.setSelectedSourceType(type)
        }
    }

    fun setAutoStartEnabled(enabled: Boolean) {
        _uiState.update { it.withAutoStartEnabled(enabled) }
        viewModelScope.launch {
            settingsRepository.setAutoStartEnabled(enabled)
        }
    }

    fun toggleStreaming(enabled: Boolean) {
        if (enabled) {
            startStreaming()
        } else {
            stopStreaming()
        }
    }

    private fun startStreaming() {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            try {
                val source = uiState.value.activeSource
                val isMatchSampleRateEnabled = settingsRepository.isMatchSampleRateEnabled()
                val isMatchChannelsEnabled = settingsRepository.isMatchChannelsEnabled()
                val preferred = settingsRepository.getPreferredFormat()
                val notifConfig = settingsRepository.getNotificationCustomization()
                val jobInstance = StreamingJob(
                    context = appContainer.context,
                    source = source,
                    serviceClass = appContainer.mediaSessionServiceClass,
                    preferredSampleRate = preferred.sampleRate,
                    preferredChannelMask = preferred.channelMask,
                    isMatchSampleRateEnabled = isMatchSampleRateEnabled,
                    isMatchChannelsEnabled = isMatchChannelsEnabled,
                    notificationConfig = notifConfig,
                    onJobError = { error ->
                        handleStreamingError(
                            toastText = "Streaming failed",
                            summary = "An error occurred mid-playback.",
                            error = error
                        )
                    },
                    onJobCompleted = {
                        stopStreaming()
                        Toast.makeText(
                            appContainer.context,
                            "VirtuMic: Streaming completed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
                jobInstance.start(playAutomatically = uiState.value.isAutoStartEnabled)
                currentStreamingJob = jobInstance
                _uiState.update { it.setStreaming(enabled = true) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start streaming", e)
                handleStreamingError(
                    toastText = "Start failed",
                    summary = "Failed to start streaming.",
                    error = e
                )
            }
        }
    }

    private fun handleStreamingError(toastText: String, summary: String, error: Throwable) {
        stopStreaming()
        Toast.makeText(
            appContainer.context,
            "VirtuMic: $toastText",
            Toast.LENGTH_SHORT
        ).show()
        _uiState.update {
            it.withPlaybackError(
                PlaybackError(
                    summary = summary,
                    technicalDetails = error.message ?: error.toString()
                )
            )
        }
    }

    fun dismissError() {
        _uiState.update { it.withPlaybackError(null) }
    }

    private fun stopStreaming() {
        activeJob?.cancel()
        viewModelScope.launch {
            currentStreamingJob?.stop()
            currentStreamingJob = null
            _uiState.update { it.setStreaming(false) }
        }
    }

    fun loadApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = true) }
            val apps = appRepository.getInstalledApps()
            _uiState.update { it.withAvailableApps(apps).copy(isLoadingApps = false) }
        }
    }

    fun onFileSelected(uri: Uri) {
        viewModelScope.launch {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            appContainer.context.contentResolver.takePersistableUriPermission(uri, flags)

            val info = audioMetadataRepository.getFileSourceInfo(uri)
            _uiState.update { it.withFileInfo(info) }
            settingsRepository.setSelectedFileUri(uri)
        }
    }

    fun onAppSelected(app: AppItem) {
        val info = app.toAppSourceInfo()
        _uiState.update { it.withAppInfo(info) }
        viewModelScope.launch {
            settingsRepository.setSelectedAppInfo(info)
        }
    }

    fun clearSelectedSource() {
        _uiState.update { it.clearSource() }
        viewModelScope.launch {
            when (uiState.value.selectedSourceType) {
                SourceType.FILE -> settingsRepository.setSelectedFileUri(null)
                SourceType.APP -> settingsRepository.setSelectedAppInfo(null)
                else -> {}
            }
        }
    }

    fun triggerPrivilegeProvision(force: Boolean = false) {
        val modeId = PrivilegeHub.state.value.selectedId
        if (modeId == PrivilegeProvider.ID_NONE) {
            _uiState.update { it.copy(showPrivilegePicker = true) }
        } else {
            viewModelScope.launch {
                PrivilegeHub.provide(force = force)
            }
        }
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

    fun onResume() {
        viewModelScope.launch {
            val state = PrivilegeHub.state.value
            if (state.selectedId != PrivilegeProvider.ID_NONE) {
                PrivilegeHub.provide(force = false)
            }
        }
    }
}
