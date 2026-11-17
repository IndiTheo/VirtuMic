package com.indidevs.virtumic.ui.component

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.indidevs.virtumic.domain.model.AudioSource
import com.indidevs.virtumic.domain.model.formatDuration

@Composable
fun GenericSourceDetails(source: AudioSource, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = source.title,
            style = MaterialTheme.typography.titleMedium,
            color = if (source is AudioSource.None) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            modifier = Modifier.basicMarquee()
        )
        Text(
            text = source.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.basicMarquee()
        )

        source.duration?.let { duration ->
            Text(
                text = "Length: ${duration.formatDuration()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
