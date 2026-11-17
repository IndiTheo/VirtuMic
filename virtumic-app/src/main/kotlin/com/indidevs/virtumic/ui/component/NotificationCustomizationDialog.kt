package com.indidevs.virtumic.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.indidevs.virtumic.domain.model.SourceType
import com.indidevs.virtumic.domain.repository.NotifField
import com.indidevs.virtumic.domain.repository.NotifFieldConfig
import com.indidevs.virtumic.domain.repository.NotifFieldRules
import com.indidevs.virtumic.domain.repository.NotifLineConfig
import com.indidevs.virtumic.domain.repository.NotifSeparator
import com.indidevs.virtumic.domain.repository.NotificationCustomizationConfig
import com.indidevs.virtumic.ui.theme.spacing

@Composable
fun NotificationCustomizationDialog(
    initialConfig: NotificationCustomizationConfig,
    onSave: (NotificationCustomizationConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var config by remember { mutableStateOf(initialConfig) }
    val tabs = remember { listOf(SourceType.FILE, SourceType.APP) }
    var selectedTab by remember { mutableStateOf(tabs.first()) }

    val fileFields = remember {
        listOf(
            NotifField.FILE_NAME,
            NotifField.ID3_TITLE,
            NotifField.ID3_ARTIST,
            NotifField.ALBUM,
            NotifField.FULL_AUDIO_FORMAT,
            NotifField.SAMPLE_RATE,
            NotifField.CHANNELS
        )
    }

    val appFields = remember {
        listOf(
            NotifField.APP_NAME,
            NotifField.PACKAGE_NAME,
            NotifField.FULL_AUDIO_FORMAT,
            NotifField.SAMPLE_RATE,
            NotifField.CHANNELS
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notification Customization") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
            ) {
                SecondaryTabRow(
                    selectedTabIndex = tabs.indexOf(selectedTab),
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tabs.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = {
                                Text(
                                    when (tab) {
                                        SourceType.FILE -> "File Sources"
                                        SourceType.APP -> "App Sources"
                                        else -> tab.displayName
                                    }
                                )
                            }
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(top = MaterialTheme.spacing.medium)
                ) {
                    when (selectedTab) {
                        SourceType.FILE -> {
                            SourceCustomizationSection(
                                titleLabel = "File Notification Title",
                                artistLabel = "File Notification Artist / Subtitle",
                                titleConfig = config.fileTitle,
                                artistConfig = config.fileArtist,
                                allowedFields = fileFields,
                                onTitleChanged = { config = config.copy(fileTitle = it) },
                                onArtistChanged = { config = config.copy(fileArtist = it) },
                                onResetDefaults = {
                                    val defTitle =
                                        NotificationCustomizationConfig.DEFAULT_FILE_TITLE
                                    val defArtist =
                                        NotificationCustomizationConfig.DEFAULT_FILE_ARTIST
                                    config = config.copy(
                                        fileTitle = defTitle,
                                        fileArtist = defArtist
                                    )
                                }
                            )
                        }
                        SourceType.APP -> {
                            SourceCustomizationSection(
                                titleLabel = "App Notification Title",
                                artistLabel = "App Notification Artist / Subtitle",
                                titleConfig = config.appTitle,
                                artistConfig = config.appArtist,
                                allowedFields = appFields,
                                onTitleChanged = { config = config.copy(appTitle = it) },
                                onArtistChanged = { config = config.copy(appArtist = it) },
                                onResetDefaults = {
                                    val defTitle =
                                        NotificationCustomizationConfig.DEFAULT_APP_TITLE
                                    val defArtist =
                                        NotificationCustomizationConfig.DEFAULT_APP_ARTIST
                                    config = config.copy(
                                        appTitle = defTitle,
                                        appArtist = defArtist
                                    )
                                }
                            )
                        }
                        else -> {}
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(config)
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SourceCustomizationSection(
    titleLabel: String,
    artistLabel: String,
    titleConfig: NotifLineConfig,
    artistConfig: NotifLineConfig,
    allowedFields: List<NotifField>,
    onTitleChanged: (NotifLineConfig) -> Unit,
    onArtistChanged: (NotifLineConfig) -> Unit,
    onResetDefaults: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = titleLabel,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f, fill = false)
        )
        TextButton(
            onClick = onResetDefaults,
            contentPadding = PaddingValues(
                horizontal = MaterialTheme.spacing.small,
                vertical = MaterialTheme.spacing.extraSmall
            )
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Reset",
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(MaterialTheme.spacing.extraSmall))
            Text("Reset", style = MaterialTheme.typography.labelMedium)
        }
    }
    LineConfigEditor(
        lineConfig = titleConfig,
        allowedFields = allowedFields,
        onChanged = onTitleChanged
    )

    Text(
        artistLabel,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = MaterialTheme.spacing.medium)
    )
    LineConfigEditor(
        lineConfig = artistConfig,
        allowedFields = allowedFields,
        allowNone = true,
        onChanged = onArtistChanged
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LineConfigEditor(
    lineConfig: NotifLineConfig,
    allowedFields: List<NotifField>,
    allowNone: Boolean = false,
    onChanged: (NotifLineConfig) -> Unit
) {
    val appendableOptions = if (allowNone) {
        listOf(
            NotifField.NONE
        ) + allowedFields
    } else {
        allowedFields
    }

    val allowedAppendable = appendableOptions.filter { field ->
        field == NotifField.NONE || field != lineConfig.primary
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.extraSmall)
    ) {
        FieldConfigSelector(
            label = "Primary Field",
            config = lineConfig.primaryConfig,
            fieldOptions = allowedFields,
            allowedFallbackOptions = allowedFields,
            onConfigChanged = { newPrimaryConfig ->
                val newAppendableConfig = if (newPrimaryConfig.field == lineConfig.appendable) {
                    NotifFieldConfig(NotifField.NONE)
                } else {
                    lineConfig.appendableConfig
                }
                onChanged(
                    lineConfig.copy(
                        primaryConfig = newPrimaryConfig,
                        appendableConfig = newAppendableConfig
                    )
                )
            }
        )

        Spacer(modifier = Modifier.padding(vertical = MaterialTheme.spacing.extraSmall))

        FieldConfigSelector(
            label = "Appendable Field",
            config = lineConfig.appendableConfig,
            fieldOptions = allowedAppendable,
            allowedFallbackOptions = allowedFields,
            onConfigChanged = { newAppendableConfig ->
                onChanged(lineConfig.copy(appendableConfig = newAppendableConfig))
            }
        )

        if (lineConfig.appendable != NotifField.NONE) {
            DropdownSelector(
                label = "Separator",
                selected = lineConfig.separator,
                options = NotifSeparator.entries.toList(),
                displayName = { it.symbol },
                onSelected = { onChanged(lineConfig.copy(separator = it)) }
            )
        }
    }
}

@Composable
private fun FieldConfigSelector(
    label: String,
    config: NotifFieldConfig,
    fieldOptions: List<NotifField>,
    allowedFallbackOptions: List<NotifField>,
    onConfigChanged: (NotifFieldConfig) -> Unit
) {
    DropdownSelector(
        label = label,
        selected = config.field,
        options = fieldOptions,
        displayName = { it.displayName },
        onSelected = { newField ->
            onConfigChanged(NotifFieldConfig(field = newField))
        }
    )

    if (NotifFieldRules.isFallbackable(config.field)) {
        val validFallbackOptions = remember(allowedFallbackOptions, config.field) {
            allowedFallbackOptions.filter {
                !NotifFieldRules.canBeMissing(it) && it != config.field
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.extraSmall, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Fallback if missing",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Switch(
                checked = config.useFallback,
                onCheckedChange = { checked ->
                    val defaultFallback = if (
                        checked && config.fallbackField !in validFallbackOptions
                    ) {
                        validFallbackOptions.firstOrNull() ?: config.fallbackField
                    } else {
                        config.fallbackField
                    }
                    onConfigChanged(
                        config.copy(useFallback = checked, fallbackField = defaultFallback)
                    )
                }
            )
        }

        if (config.useFallback) {
            DropdownSelector(
                label = "Fallback Field",
                selected = config.fallbackField,
                options = validFallbackOptions,
                displayName = { it.displayName },
                onSelected = { newFallback ->
                    onConfigChanged(config.copy(fallbackField = newFallback))
                }
            )
        }

        val hintText = if (config.useFallback) {
            "If missing, falls back to: ${config.fallbackField.displayName}"
        } else {
            "If missing, displays: \"${config.field.unknownPlaceholder ?: ""}\""
        }
        Text(
            text = hintText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                start = MaterialTheme.spacing.medium,
                bottom = MaterialTheme.spacing.extraSmall
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DropdownSelector(
    label: String,
    selected: T,
    options: List<T>,
    displayName: (T) -> String,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.extraSmall)
    ) {
        OutlinedTextField(
            value = displayName(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(
                ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                true
            ).fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(displayName(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
