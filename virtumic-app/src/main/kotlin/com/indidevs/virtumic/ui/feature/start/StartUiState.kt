package com.indidevs.virtumic.ui.feature.start

import androidx.compose.runtime.Immutable
import com.indidevs.android.privilegehub.PrivilegeProvider
import com.indidevs.virtumic.domain.model.AppSourceInfo
import com.indidevs.virtumic.domain.model.AudioSource
import com.indidevs.virtumic.domain.model.FileSourceInfo
import com.indidevs.virtumic.domain.model.SourceType
import com.indidevs.virtumic.domain.model.StreamingPrivilege
import com.indidevs.virtumic.ui.model.AppItem

@Immutable
data class PlaybackError(
    val summary: String,
    val technicalDetails: String? = null
)

@Immutable
data class StartUiState(
    val fileInfo: FileSourceInfo? = null,
    val appInfo: AppSourceInfo? = null,
    val availableApps: List<AppItem> = emptyList(),
    val isLoadingApps: Boolean = false,
    val selectedSourceType: SourceType = SourceType.FILE,
    val isStreaming: Boolean = false,
    val isMediaSessionConnected: Boolean = false,
    val isMediaSessionStreaming: Boolean = false,
    val showPrivilegePicker: Boolean = false,
    val privilegeMode: String = PrivilegeProvider.ID_NONE,
    val privilegeStatus: StreamingPrivilege = StreamingPrivilege.NOT_READY,
    val isAutoStartEnabled: Boolean = true,
    val playbackError: PlaybackError? = null
) {
    val activeSource: AudioSource
        get() = when (selectedSourceType) {
            SourceType.FILE -> fileInfo?.let { AudioSource.File(it) }
                ?: AudioSource.None(selectedSourceType.name.lowercase())
            SourceType.APP -> appInfo?.let { AudioSource.App(it) }
                ?: AudioSource.None(selectedSourceType.name.lowercase())
            else -> AudioSource.None(selectedSourceType.name.lowercase())
        }

    val canToggleStreaming: Boolean get() = activeSource.isReady &&
        privilegeStatus == StreamingPrivilege.READY

    fun setSourceType(type: SourceType): StartUiState =
        if (isStreaming) this else copy(selectedSourceType = type)

    fun setStreaming(enabled: Boolean): StartUiState =
        if (enabled && !canToggleStreaming) this else copy(isStreaming = enabled)

    fun clearSource(): StartUiState = if (isStreaming) {
        this
    } else {
        when (selectedSourceType) {
            SourceType.FILE -> copy(fileInfo = null)
            SourceType.APP -> copy(appInfo = null)
            else -> this
        }
    }

    fun withFileInfo(info: FileSourceInfo?): StartUiState = copy(fileInfo = info)
    fun withAppInfo(info: AppSourceInfo?): StartUiState = copy(appInfo = info)
    fun withAvailableApps(apps: List<AppItem>): StartUiState = copy(availableApps = apps)
    fun withAutoStartEnabled(enabled: Boolean): StartUiState = copy(isAutoStartEnabled = enabled)
    fun withPlaybackError(error: PlaybackError?): StartUiState = copy(playbackError = error)
}
