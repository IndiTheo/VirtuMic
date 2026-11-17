package com.indidevs.virtumic.ui.feature.start

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.indidevs.virtumic.domain.model.AudioSource
import com.indidevs.virtumic.domain.model.SourceType
import com.indidevs.virtumic.domain.model.StreamingPrivilege
import com.indidevs.virtumic.ui.component.AppPicker
import com.indidevs.virtumic.ui.component.EnableStreamingToggle
import com.indidevs.virtumic.ui.component.ErrorDialog
import com.indidevs.virtumic.ui.component.OptionToggleCard
import com.indidevs.virtumic.ui.component.PrivilegeModePicker
import com.indidevs.virtumic.ui.component.SourceCard
import com.indidevs.virtumic.ui.component.SourceSelector
import com.indidevs.virtumic.ui.theme.spacing

@Composable
fun StartScreen(
    modifier: Modifier = Modifier,
    columns: Int = 2,
    viewModel: StartViewModel = viewModel(factory = StartViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val audioSourceSpecs = remember {
        listOf(
            SourceSpec(SourceType.FILE, Icons.Default.MusicNote),
            SourceSpec(SourceType.APP, Icons.Default.Apps)
        )
    }

    var showAppPicker by rememberSaveable { mutableStateOf(value = false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.onFileSelected(it) }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val onSelectClick = {
        if (uiState.selectedSourceType == SourceType.FILE) {
            filePickerLauncher.launch(arrayOf("audio/*"))
        } else {
            viewModel.loadApps()
            showAppPicker = true
        }
    }

    StartScreenContent(
        isLandscape = isLandscape,
        audioSourceSpecs = audioSourceSpecs,
        selectedSourceType = uiState.selectedSourceType,
        isStreaming = uiState.isStreaming,
        canEnable = uiState.canToggleStreaming,
        isAutoStartEnabled = uiState.isAutoStartEnabled,
        privilegeStatus = uiState.privilegeStatus,
        activeSource = uiState.activeSource,
        columns = columns,
        onSourceTypeSelect = viewModel::setSourceType,
        onSelectClick = onSelectClick,
        onClearClick = viewModel::clearSelectedSource,
        onToggleStreaming = viewModel::toggleStreaming,
        onAutoStartToggle = viewModel::setAutoStartEnabled,
        onProvidePrivilegeClick = viewModel::triggerPrivilegeProvision,
        modifier = modifier
    )

    if (showAppPicker) {
        AppPicker(
            apps = uiState.availableApps,
            isLoading = uiState.isLoadingApps,
            onAppSelected = { viewModel.onAppSelected(it) },
            onDismiss = { showAppPicker = false }
        )
    }

    if (uiState.showPrivilegePicker) {
        PrivilegeModePicker(
            currentMode = uiState.privilegeMode,
            onModeSelected = viewModel::onPrivilegeModeSelected,
            onDismiss = viewModel::dismissPrivilegePicker
        )
    }

    uiState.playbackError?.let { error ->
        ErrorDialog(
            summary = error.summary,
            technicalDetails = error.technicalDetails,
            onDismiss = viewModel::dismissError
        )
    }
}

@Composable
private fun StartScreenContent(
    isLandscape: Boolean,
    audioSourceSpecs: List<SourceSpec>,
    selectedSourceType: SourceType,
    isStreaming: Boolean,
    canEnable: Boolean,
    isAutoStartEnabled: Boolean,
    privilegeStatus: StreamingPrivilege,
    activeSource: AudioSource,
    columns: Int,
    onSourceTypeSelect: (SourceType) -> Unit,
    onSelectClick: () -> Unit,
    onClearClick: () -> Unit,
    onToggleStreaming: (Boolean) -> Unit,
    onAutoStartToggle: (Boolean) -> Unit,
    onProvidePrivilegeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = MaterialTheme.spacing.medium
    val contentModifier = modifier
        .fillMaxSize()
        .padding(
            start = if (isLandscape) MaterialTheme.spacing.small else MaterialTheme.spacing.large,
            top = MaterialTheme.spacing.large,
            end = MaterialTheme.spacing.large,
            bottom = MaterialTheme.spacing.large
        )

    val indicators = @Composable {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.Start)
        ) {
            StateIndicator(
                label = "Privilege Host",
                color = when (privilegeStatus) {
                    StreamingPrivilege.READY -> Color.Green
                    StreamingPrivilege.STARTING -> Color.Yellow
                    else -> Color.Red
                }
            )
        }
    }

    if (isLandscape) {
        Row(
            modifier = contentModifier,
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                indicators()
                Spacer(modifier = Modifier.weight(1f))
                SourceGrid(
                    audioSourceSpecs = audioSourceSpecs,
                    selectedSourceType = selectedSourceType,
                    isStreaming = isStreaming,
                    columns = columns,
                    onSourceTypeSelect = onSourceTypeSelect,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.weight(1f))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(spacing)
            ) {
                SourceSelector(
                    activeSource = activeSource,
                    enabled = !isStreaming,
                    onSelectClick = onSelectClick,
                    onClearClick = onClearClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                OptionToggleCard(
                    label = "Play right away",
                    checked = isAutoStartEnabled,
                    onCheckedChange = onAutoStartToggle,
                    enabled = !isStreaming,
                    modifier = Modifier.fillMaxWidth()
                )

                val showToggle = privilegeStatus != StreamingPrivilege.NOT_READY

                if (showToggle) {
                    EnableStreamingToggle(
                        isStreamingActive = isStreaming,
                        isInteractive = canEnable,
                        onToggle = onToggleStreaming,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Button(
                        onClick = onProvidePrivilegeClick,
                        enabled = true,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (privilegeStatus == StreamingPrivilege.STARTING) {
                            HardwareFrameSpinner(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(MaterialTheme.spacing.medium))
                        }
                        Text("Provide privilege to stream")
                    }
                }
            }
        }
    } else {
        Column(
            modifier = contentModifier
        ) {
            indicators()
            Spacer(modifier = Modifier.weight(1f))
            SourceGrid(
                audioSourceSpecs = audioSourceSpecs,
                selectedSourceType = selectedSourceType,
                isStreaming = isStreaming,
                columns = columns,
                onSourceTypeSelect = onSourceTypeSelect,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.size(spacing))
            ActionControls(
                activeSource = activeSource,
                isStreaming = isStreaming,
                canEnable = canEnable,
                isAutoStartEnabled = isAutoStartEnabled,
                onAutoStartToggle = onAutoStartToggle,
                privilegeStatus = privilegeStatus,
                onSelectClick = onSelectClick,
                onClearClick = onClearClick,
                onToggleStreaming = onToggleStreaming,
                onProvidePrivilegeClick = onProvidePrivilegeClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StateIndicator(label: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            drawCircle(color = color)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall
        )
    }
}

@Composable
private fun SourceGrid(
    audioSourceSpecs: List<SourceSpec>,
    selectedSourceType: SourceType,
    isStreaming: Boolean,
    columns: Int,
    onSourceTypeSelect: (SourceType) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = MaterialTheme.spacing.medium
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        audioSourceSpecs.chunked(columns).forEach { rowSpecs ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                rowSpecs.forEach { spec ->
                    SourceCard(
                        name = spec.type.displayName,
                        icon = spec.icon,
                        isSelected = selectedSourceType == spec.type,
                        enabled = !isStreaming,
                        onSelect = { onSourceTypeSelect(spec.type) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(columns - rowSpecs.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ActionControls(
    activeSource: AudioSource,
    isStreaming: Boolean,
    canEnable: Boolean,
    isAutoStartEnabled: Boolean,
    onAutoStartToggle: (Boolean) -> Unit,
    privilegeStatus: StreamingPrivilege,
    onSelectClick: () -> Unit,
    onClearClick: () -> Unit,
    onToggleStreaming: (Boolean) -> Unit,
    onProvidePrivilegeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = MaterialTheme.spacing.medium
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        SourceSelector(
            activeSource = activeSource,
            enabled = !isStreaming,
            onSelectClick = onSelectClick,
            onClearClick = onClearClick,
            modifier = Modifier.fillMaxWidth()
        )

        OptionToggleCard(
            label = "Play right away",
            checked = isAutoStartEnabled,
            onCheckedChange = onAutoStartToggle,
            enabled = !isStreaming,
            modifier = Modifier.fillMaxWidth()
        )

        val showToggle = privilegeStatus != StreamingPrivilege.NOT_READY

        if (showToggle) {
            EnableStreamingToggle(
                isStreamingActive = isStreaming,
                isInteractive = canEnable,
                onToggle = onToggleStreaming,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Button(
                onClick = onProvidePrivilegeClick,
                enabled = true,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (privilegeStatus == StreamingPrivilege.STARTING) {
                    HardwareFrameSpinner(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(MaterialTheme.spacing.medium))
                }
                Text("Provide privilege to stream")
            }
        }
    }
}

/**
 * A spinner that bypasses system animation scales by manually driving rotation
 * from the Hardware Monotonic Frame Clock. This is guaranteed to spin as long
 * as the UI thread is processing frames.
 */
@Composable
private fun HardwareFrameSpinner(modifier: Modifier = Modifier, color: Color) {
    val frameTime by produceState(0L) {
        while (true) {
            withFrameMillis { value = it }
        }
    }

    Canvas(modifier = modifier) {
        val rotation = (frameTime % 1000) / 1000f * 360f
        val strokeWidth = 2.dp.toPx()
        drawArc(
            color = color,
            startAngle = rotation,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            size = Size(size.width - strokeWidth, size.height - strokeWidth)
        )
    }
}

private data class SourceSpec(val type: SourceType, val icon: ImageVector)
