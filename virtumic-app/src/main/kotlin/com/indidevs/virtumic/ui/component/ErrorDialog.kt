package com.indidevs.virtumic.ui.component

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.indidevs.virtumic.ui.theme.spacing
import kotlinx.coroutines.launch

@Composable
fun ErrorDialog(
    summary: String,
    technicalDetails: String? = null,
    title: String = "Playback Error",
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showNerdDetails by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)
            ) {
                Text(text = summary, style = MaterialTheme.typography.bodyMedium)

                if (!technicalDetails.isNullOrBlank()) {
                    TextButton(
                        onClick = { showNerdDetails = !showNerdDetails },
                        contentPadding = PaddingValues(
                            horizontal = 0.dp,
                            vertical = MaterialTheme.spacing.extraSmall
                        )
                    ) {
                        Text(if (showNerdDetails) "Hide details" else "I'm a nerd")
                    }

                    if (showNerdDetails) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = technicalDetails,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        clipboard.setClipEntry(
                                            ClipEntry(
                                                ClipData.newPlainText(
                                                    "Error Details",
                                                    technicalDetails
                                                )
                                            )
                                        )
                                        Toast.makeText(
                                            context,
                                            "Copied to clipboard",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy error"
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                }
            ) {
                Text("OK")
            }
        },
        modifier = modifier
    )
}
