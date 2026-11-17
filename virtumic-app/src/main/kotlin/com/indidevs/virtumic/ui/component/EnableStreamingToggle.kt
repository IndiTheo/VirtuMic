package com.indidevs.virtumic.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.indidevs.virtumic.ui.theme.spacing

@Composable
fun EnableStreamingToggle(
    isStreamingActive: Boolean,
    isInteractive: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val status = if (isStreamingActive) "Enabled" else "Disabled"
    val containerColor = when {
        isStreamingActive -> MaterialTheme.colorScheme.primaryContainer
        !isInteractive -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isStreamingActive) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(
            alpha = if (isInteractive) 1f else 0.38f
        )
    }

    Surface(
        onClick = { onToggle(!isStreamingActive) },
        enabled = isInteractive || isStreamingActive,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Streaming $status",
                style = MaterialTheme.typography.titleLarge
            )
            Switch(
                checked = isStreamingActive,
                onCheckedChange = null,
                enabled = isInteractive || isStreamingActive
            )
        }
    }
}
