package com.indidevs.virtumic.streaming

import android.content.ComponentName
import android.content.Context
import android.media.AudioFormat
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors
import com.indidevs.virtumic.domain.model.AppSourceInfo
import com.indidevs.virtumic.domain.model.AudioSource
import com.indidevs.virtumic.domain.model.FileSourceInfo
import com.indidevs.virtumic.domain.repository.NotificationCustomizationConfig
import com.indidevs.virtumic.domain.repository.SettingsRepository
import com.indidevs.virtumic.service.model.AudioConfig
import com.indidevs.virtumic.service.session.VMicServiceContract
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class StreamingJob(
    private val context: Context,
    private val source: AudioSource,
    private val serviceClass: Class<*>,
    private val preferredSampleRate: Int = SettingsRepository.DEFAULT_SAMPLE_RATE,
    private val preferredChannelMask: Int = SettingsRepository.DEFAULT_CHANNEL_MASK,
    private val isMatchSampleRateEnabled: Boolean = true,
    private val isMatchChannelsEnabled: Boolean = true,
    private val notificationConfig: NotificationCustomizationConfig =
        NotificationCustomizationConfig(),
    private val onJobError: (Throwable) -> Unit = {},
    private val onJobCompleted: () -> Unit = {}
) {
    companion object {
        private const val TAG = "VMicStreamingJob"
    }

    private var mediaController: MediaController? = null

    internal val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "MediaController player error", error)
            onJobError(error)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                Log.i(TAG, "Playback completed naturally")
                onJobCompleted()
            }
        }
    }

    /**
     * Starts the streaming job and suspends until the MediaController is connected and playing.
     */
    suspend fun start(playAutomatically: Boolean = true) {
        try {
            when (source) {
                is AudioSource.File -> startFileStream(source.info, playAutomatically)
                is AudioSource.App -> startAppStream(source.info, playAutomatically)
                else -> throw IllegalArgumentException(
                    "Unsupported source type: ${source::class.simpleName}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start streaming job", e)
            throw e
        }
    }

    fun stop() {
        Log.i(TAG, "Stopping streaming job...")
        try {
            mediaController?.stop()
            mediaController?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping media controller", e)
        } finally {
            mediaController = null
        }
    }

    private fun preferredFormat(): AudioFormat = AudioFormat.Builder()
        .setSampleRate(preferredSampleRate)
        .setChannelMask(preferredChannelMask)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .build()

    private fun buildItem(
        uri: Uri,
        title: String,
        artist: String,
        artBytes: ByteArray? = null,
        micFormat: AudioFormat,
        targetPresets: IntArray = intArrayOf()
    ): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
        if (artBytes != null) {
            metadataBuilder.setArtworkData(artBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }
        val metadata = metadataBuilder
            .setExtras(
                Bundle().apply {
                    putParcelable(
                        VMicServiceContract.EXTRA_AUDIO_CONFIG,
                        AudioConfig(format = micFormat, targetPresets = targetPresets) as Parcelable
                    )
                }
            )
            .build()
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    private suspend fun connectAndStream(
        playAutomatically: Boolean,
        logTag: String,
        mediaItemBuilder: () -> MediaItem
    ): Unit = suspendCancellableCoroutine { continuation ->
        Log.i(TAG, "Starting MediaController connection for $logTag")

        val sessionToken = SessionToken(
            context,
            ComponentName(context, serviceClass)
        )

        val connectionHints = Bundle().apply {
            putString(
                VMicServiceContract.CONNECTION_HINT_MIC_ID,
                VMicServiceContract.DEFAULT_MIC_ID
            )
        }

        val controllerFuture = MediaController.Builder(context, sessionToken)
            .setConnectionHints(connectionHints)
            .buildAsync()

        controllerFuture.addListener(
            {
                try {
                    val controller = Futures.getDone(controllerFuture)
                    mediaController = controller
                    controller.addListener(playerListener)

                    val mediaItem = mediaItemBuilder()

                    controller.apply {
                        setMediaItem(mediaItem)
                        prepare()
                        if (playAutomatically) {
                            play()
                        }
                    }

                    Log.i(TAG, "MediaController connected and play requested: $logTag")
                    if (continuation.isActive) continuation.resume(Unit)
                } catch (e: ExecutionException) {
                    val cause = e.cause ?: e
                    Log.e(TAG, "MediaController connection failed ($logTag)", cause)
                    if (continuation.isActive) continuation.resumeWithException(cause)
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error during MediaController setup ($logTag)", e)
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            },
            MoreExecutors.directExecutor()
        )

        continuation.invokeOnCancellation {
            controllerFuture.cancel(true)
            mediaController?.release()
        }
    }

    private suspend fun startAppStream(info: AppSourceInfo, playAutomatically: Boolean) {
        connectAndStream(playAutomatically, "App ${info.appName}") {
            val preferred = preferredFormat()
            val titleText = NotificationFormatter.formatTitle(
                notificationConfig.appTitle,
                null,
                info,
                preferred,
                notificationConfig.appArtist
            )
            val artistText = NotificationFormatter.formatArtist(
                notificationConfig.appArtist,
                null,
                info,
                preferred,
                notificationConfig.appTitle
            )

            buildItem(
                uri = Uri.parse("appaudio://capture?pkg=${info.packageName}"),
                title = titleText,
                artist = artistText,
                artBytes = info.iconBytes,
                micFormat = preferred
            )
        }
    }

    private suspend fun startFileStream(info: FileSourceInfo, playAutomatically: Boolean) {
        connectAndStream(playAutomatically, "File ${info.fileName}") {
            val preferred = preferredFormat()
            val sampleRate = if (isMatchSampleRateEnabled) {
                info.sampleRate
            } else {
                preferred.sampleRate
            }
            val channelCount = if (isMatchChannelsEnabled) {
                info.channelCount
            } else {
                if (preferred.channelMask == AudioFormat.CHANNEL_OUT_MONO) 1 else 2
            }
            val channelMask = if (channelCount == 1) {
                AudioFormat.CHANNEL_OUT_MONO
            } else {
                AudioFormat.CHANNEL_OUT_STEREO
            }
            val encoding = AudioFormat.ENCODING_PCM_16BIT

            val micFormat = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(channelMask)
                .setEncoding(encoding)
                .build()

            val titleText = NotificationFormatter.formatTitle(
                notificationConfig.fileTitle,
                info,
                null,
                micFormat,
                notificationConfig.fileArtist
            )
            val artistText = NotificationFormatter.formatArtist(
                notificationConfig.fileArtist,
                info,
                null,
                micFormat,
                notificationConfig.fileTitle
            )

            buildItem(
                uri = info.uri,
                title = titleText,
                artist = artistText,
                micFormat = micFormat
            )
        }
    }
}
