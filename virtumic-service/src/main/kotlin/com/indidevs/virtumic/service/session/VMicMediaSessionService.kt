package com.indidevs.virtumic.service.session

import android.media.AudioFormat
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.indidevs.virtumic.core.AudioMission
import com.indidevs.virtumic.media3.AudioTrackRenderersFactory
import com.indidevs.virtumic.media3.VMicMediaSourceFactory
import com.indidevs.virtumic.service.integration.VMicEngineProvider
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * A MediaSessionService that runs inside the instrumentation process.
 * Since it shares the same process as the app but has adopted shell permissions,
 * it can manage privileged session directly.
 *
 * Controllers submit fully-decorated [MediaItem]s (title/artist/subtitle already set) and
 * an [com.indidevs.virtumic.service.model.AudioConfig] in the extras carrying the mic format;
 * this service just renders them. Mic format is reconfigured per item transition, never
 * mid-playback.
 */
@OptIn(UnstableApi::class)
class VMicMediaSessionService : MediaSessionService() {

    companion object {
        private const val TAG = "VMicMediaService"

        private val DEFAULT_AUDIO_FORMAT = AudioFormat.Builder()
            .setSampleRate(48000)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val orchestrator =
        VMicSessionOrchestrator { (applicationContext as? VMicEngineProvider)?.engine }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        val requestedMicId = controllerInfo.connectionHints.getString(
            VMicServiceContract.CONNECTION_HINT_MIC_ID
        ) ?: VMicServiceContract.DEFAULT_MIC_ID
        orchestrator.getSession(requestedMicId)?.let { return it }

        Log.i(TAG, "Creating session for mic $requestedMicId")

        val isCurrentItemSilent = AtomicBoolean(false)

        val renderersFactory = AudioTrackRenderersFactory(
            context = this,
            targetFormatProvider = { orchestrator.getHandle(requestedMicId)?.format },
            shouldDiscardAudioProvider = { isCurrentItemSilent.get() }
        ) {
            if (isCurrentItemSilent.get()) {
                // Shouldn't create a track, injection is handled elsewhere
                null
            } else {
                orchestrator.createPlayerInjectionTrack(requestedMicId)
            }
        }

        val exoPlayer = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(VMicMediaSourceFactory(this))
            .build()

        val player = object : ForwardingPlayer(exoPlayer) {
            override fun play() {
                serviceScope.launch(Dispatchers.Main.immediate) {
                    try {
                        orchestrator.startPatches(requestedMicId)
                        super.play()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start patches for mic $requestedMicId", e)
                        orchestrator.stopStreaming(requestedMicId)
                    }
                }
            }
            override fun pause() {
                orchestrator.pausePatches(requestedMicId)
                super.pause()
            }
            override fun stop() {
                super.stop()
                orchestrator.releaseSession(requestedMicId)
            }
        }

        val mediaSession = MediaSession.Builder(this, player)
            .setId(requestedMicId)
            .build()
        orchestrator.setSession(requestedMicId, mediaSession)

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(TAG, "Playback error on mic $requestedMicId: ${error.message}", error)
                orchestrator.stopStreaming(requestedMicId)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                isCurrentItemSilent.set(
                    mediaItem?.let { VMicStreamPolicy.shouldSilencePlayer(it) } ?: false
                )
                if (mediaItem == null) return
                Log.i(
                    TAG,
                    "onMediaItemTransition: title='${mediaItem.mediaMetadata.title}', artist='${mediaItem.mediaMetadata.artist}', uri='${mediaItem.localConfiguration?.uri}'"
                )

                val config = VMicMediaItemParser.resolveAudioConfig(mediaItem)
                val mission = AudioMission(
                    format = config?.format ?: DEFAULT_AUDIO_FORMAT,
                    presets = config?.targetPresets
                )
                orchestrator.deployMic(requestedMicId, mission)

                val capturedAppUid = VMicMediaItemParser.resolveCapturedAppUid(
                    this@VMicMediaSessionService,
                    mediaItem
                )
                if (capturedAppUid > -1) {
                    orchestrator.sourceApp(
                        requestedMicId,
                        capturedAppUid,
                        config?.targetPresets,
                        player.isPlaying
                    )
                }
            }
        })

        return mediaSession
    }

    override fun onDestroy() {
        Log.i(TAG, "Service destroying")
        orchestrator.releaseAll()
        serviceScope.cancel()
        super.onDestroy()
    }
}
