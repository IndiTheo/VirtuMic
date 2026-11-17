package com.indidevs.virtumic.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.indidevs.virtumic.ui.theme.spacing

@Composable
fun OptionToggleCard(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val containerColor = when {
        checked -> MaterialTheme.colorScheme.primaryContainer
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (checked) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f)
    }

    Surface(
        onClick = { onCheckedChange(!checked) },
        enabled = enabled,
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
                text = label,
                style = MaterialTheme.typography.titleLarge
            )

            Surface(
                modifier = Modifier.size(28.dp),
                shape = MaterialTheme.shapes.medium,
                color = if (checked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                border = if (checked) {
                    null
                } else {
                    BorderStroke(
                        2.dp,
                        MaterialTheme.colorScheme.outline
                    )
                }
            ) {
                if (checked) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.padding(2.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}
