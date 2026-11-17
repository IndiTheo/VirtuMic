package com.indidevs.virtumic.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.indidevs.android.privilegehub.HintType
import com.indidevs.android.privilegehub.PrivilegeHub
import com.indidevs.virtumic.ui.theme.semanticColors
import com.indidevs.virtumic.ui.theme.spacing

@Composable
fun PrivilegeModePicker(
    currentMode: String,
    onModeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val modes = remember { PrivilegeHub.getProviders().sortedByDescending { it.priority } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(text = "Privilege Picker")
                Text(
                    text = "A privilege provider must be selected to use the app",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = MaterialTheme.spacing.extraSmall)
                )
            }
        },
        text = {
            Column(Modifier.selectableGroup()) {
                modes.forEach { provider ->
                    val modeId = provider.id
                    val hints by provider.hints.collectAsStateWithLifecycle()
                    val isAvailable = provider.isAvailable

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (modeId == currentMode),
                                onClick = { onModeSelected(modeId) },
                                enabled = isAvailable,
                                role = Role.RadioButton
                            )
                            .alpha(if (isAvailable) 1f else 0.5f)
                            .padding(vertical = MaterialTheme.spacing.semiMedium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (modeId == currentMode),
                            onClick = null,
                            enabled = isAvailable
                        )

                        val neutralColor = MaterialTheme.colorScheme.onSurfaceVariant
                        val semanticColors = MaterialTheme.semanticColors
                        val annotatedHints = buildAnnotatedString {
                            hints.toList().forEachIndexed { index, hint ->
                                if (index > 0) {
                                    append(" • ")
                                }
                                val color = when (hint.type) {
                                    HintType.SUCCESS -> semanticColors.success
                                    HintType.ERROR -> semanticColors.error
                                    HintType.WARNING -> semanticColors.warning
                                    else -> neutralColor
                                }
                                withStyle(style = SpanStyle(color = color)) {
                                    append(hint.text)
                                }
                            }
                        }

                        Column(Modifier.weight(1f).padding(start = MaterialTheme.spacing.medium)) {
                            Text(
                                text = provider.name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (hints.isNotEmpty()) {
                                Text(
                                    text = annotatedHints,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        val isSuccess = hints.any { hint -> hint.type == HintType.SUCCESS }
                        if (isSuccess) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.semanticColors.success,
                                modifier = Modifier.padding(start = MaterialTheme.spacing.small)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
