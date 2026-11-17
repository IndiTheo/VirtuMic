package com.indidevs.virtumic.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Html
import android.util.Log
import com.indidevs.virtumic.domain.model.FileSourceInfo
import com.indidevs.virtumic.domain.repository.AudioMetadataRepository
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultAudioMetadataRepository(private val context: Context) : AudioMetadataRepository {
    override suspend fun getFileSourceInfo(uri: Uri): FileSourceInfo? =
        withContext(Dispatchers.IO) {
            try {
                var fileName = "Audio File"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex != -1) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
                fileName = fileName.unescapeHtml() ?: "Audio File"

                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)

                val durationStr = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )
                val duration = Duration.ofMillis(durationStr?.toLongOrNull() ?: 0L)

                val sampleRateStr = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_SAMPLERATE
                )
                val sampleRate = sampleRateStr?.toIntOrNull() ?: 44100

                val channelCountStr = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS
                )
                val channelCount = channelCountStr?.toIntOrNull() ?: 2

                val format = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_MIMETYPE
                ) ?: "audio/mpeg"

                val title = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_TITLE
                ).unescapeHtml()
                val artist = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_ARTIST
                ).unescapeHtml()
                val album = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_ALBUM
                ).unescapeHtml()

                retriever.release()

                FileSourceInfo(
                    uri = uri,
                    fileName = fileName,
                    format = format,
                    duration = duration,
                    sampleRate = sampleRate,
                    channelCount = channelCount,
                    title = title,
                    artist = artist,
                    album = album
                )
            } catch (e: Exception) {
                Log.e("AudioMetadataRepo", "Failed to get metadata for $uri", e)
                null
            }
        }

    private fun String?.unescapeHtml(): String? {
        if (this == null) return null
        return try {
            Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY).toString()
        } catch (_: Exception) {
            this
        }
    }
}
