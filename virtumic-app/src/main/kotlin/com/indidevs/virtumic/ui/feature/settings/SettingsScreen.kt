package com.indidevs.virtumic.ui.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.SettingsMenuLink
import com.alorma.compose.settings.ui.SettingsSwitch
import com.indidevs.virtumic.MicApplication
import com.indidevs.virtumic.data.repository.DefaultSettingsRepository
import com.indidevs.virtumic.ui.component.AudioFormatPicker
import com.indidevs.virtumic.ui.component.NotificationCustomizationDialog
import com.indidevs.virtumic.ui.component.PrivilegeModePicker
import com.indidevs.virtumic.ui.theme.MyApplicationTheme

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize()
    ) {
        item {
            SettingsGroup(title = { Text("Operations") }) {
                SettingsMenuLink(
                    title = { Text("Privilege Provider") },
                    subtitle = {
                        Text(text = uiState.privilegeDisplayName)
                    },
                    icon = { Icon(Icons.Default.Security, contentDescription = null) },
                    onClick = viewModel::onPrivilegeProviderClick
                )
            }
        }

        item {
            SettingsGroup(title = { Text("Audio Engine") }) {
                RecommendedSettingsSwitch(
                    state = uiState.isMatchSampleRateEnabled,
                    title = "Match Source Sample Rate",
                    subtitle = "Automatically match file sample rate",
                    icon = Icons.Default.AudioFile,
                    onCheckedChange = viewModel::setMatchSampleRateEnabled
                )
                RecommendedSettingsSwitch(
                    state = uiState.isMatchChannelsEnabled,
                    title = "Match Source Channels",
                    subtitle = "Automatically match channel layout (Mono/Stereo)",
                    icon = Icons.Default.AudioFile,
                    onCheckedChange = viewModel::setMatchChannelsEnabled
                )
                SettingsMenuLink(
                    title = { Text("Preferred Format") },
                    subtitle = {
                        val channelStr = if (uiState.channelMask ==
                            android.media.AudioFormat.CHANNEL_OUT_STEREO
                        ) {
                            "Stereo"
                        } else {
                            "Mono"
                        }
                        Text("${uiState.sampleRate} Hz • $channelStr • 16-bit")
                    },
                    icon = {
                        Icon(
                            Icons.Default.SettingsInputComponent,
                            contentDescription = null
                        )
                    },
                    onClick = viewModel::onFormatSettingsClick
                )
            }
        }

        item {
            SettingsGroup(title = { Text("Customization") }) {
                SettingsMenuLink(
                    title = { Text("Notification Format") },
                    subtitle = { Text("Customize titles and artist/description lines") },
                    icon = { Icon(Icons.Default.Tune, contentDescription = null) },
                    onClick = viewModel::onCustomizationClick
                )
            }
        }
    }

    if (uiState.showPrivilegePicker) {
        PrivilegeModePicker(
            currentMode = uiState.privilegeMode,
            onModeSelected = viewModel::onPrivilegeModeSelected,
            onDismiss = viewModel::dismissPrivilegePicker
        )
    }

    if (uiState.showFormatPicker) {
        AudioFormatPicker(
            currentSampleRate = uiState.sampleRate,
            currentChannelMask = uiState.channelMask,
            onFormatSelected = viewModel::updateFormat,
            onDismiss = viewModel::dismissFormatPicker
        )
    }

    if (uiState.showNotificationCustomization) {
        NotificationCustomizationDialog(
            initialConfig = uiState.notificationConfig,
            onSave = viewModel::saveCustomization,
            onDismiss = viewModel::dismissCustomization
        )
    }
}

@Composable
private fun RecommendedSettingsSwitch(
    state: Boolean,
    title: String,
    subtitle: String,
    icon: ImageVector,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsSwitch(
        modifier = modifier,
        state = state,
        title = { Text(title) },
        subtitle = {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(subtitle)
                RecommendedBadge()
            }
        },
        icon = { Icon(icon, contentDescription = null) },
        onCheckedChange = onCheckedChange
    )
}

@Composable
private fun RecommendedBadge() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = "Recommended",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    MyApplicationTheme {
        SettingsScreen(
            viewModel = SettingsViewModel(
                appContainer = checkNotNull(
                    LocalContext.current.applicationContext as? MicApplication
                ).appContainer,
                settingsRepository = DefaultSettingsRepository(LocalContext.current)
            )
        )
    }
}
