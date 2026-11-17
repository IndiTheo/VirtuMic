package com.indidevs.virtumic.ui.component

import android.media.AudioFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun AudioFormatPicker(
    currentSampleRate: Int,
    currentChannelMask: Int,
    onFormatSelected: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Preferred Audio Format") },
        text = {
            Column {
                Text("Sample Rate", style = MaterialTheme.typography.titleSmall)
                listOf(
                    8000 to "8 kHz (Narrowband)",
                    11025 to "11.025 kHz",
                    16000 to "16 kHz (Wideband)",
                    32000 to "32 kHz (HD Voice)",
                    44100 to "44.1 kHz (CD)",
                    48000 to "48 kHz (Pro)",
                    192000 to "192 kHz"
                ).forEach { (rate, label) ->
                    FormatOption(
                        text = label,
                        selected = (rate == currentSampleRate),
                        onClick = { onFormatSelected(rate, currentChannelMask) }
                    )
                }

                Text(
                    "Channels",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp)
                )
                listOf(
                    AudioFormat.CHANNEL_OUT_MONO to "Mono",
                    AudioFormat.CHANNEL_OUT_STEREO to "Stereo"
                ).forEach { (mask, label) ->
                    FormatOption(
                        text = label,
                        selected = (mask == currentChannelMask),
                        onClick = { onFormatSelected(currentSampleRate, mask) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun FormatOption(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
