package com.indidevs.virtumic.core.internal.platform

import android.content.Context
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.indidevs.virtumic.core.AudioMission
import com.indidevs.virtumic.core.AudioPatch
import com.indidevs.virtumic.core.VMicEngine
import com.indidevs.virtumic.core.VMicHandle
import com.indidevs.virtumic.core.internal.mechanism.PcmTransferJob
import com.indidevs.virtumic.core.internal.platform.policy.StandardLoopbackPolicySession
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Standard implementation of [VMicEngine].
 */
internal class StandardVMicEngine(private val context: Context) : VMicEngine {
    override fun deployMic(mission: AudioMission): VMicHandle {
        val format = mission.format
        // Enforce 16-bit PCM (ENCODING_PCM_16BIT): Android's REMOTE_SUBMIX and virtual
        // microphone injection loopbacks strictly require 16-bit PCM integer. Floating-point
        // or 32-bit capture/injection is not supported by the underlying HAL and causes
        // AudioFlinger track rejection (dead IAudioTrack).
        val pcm16Format = AudioFormat.Builder()
            .setSampleRate(format.sampleRate)
            .setChannelMask(format.channelMask)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()
        val normalizedMission = mission.withFormat(pcm16Format)
        return StandardVMicHandle(context, normalizedMission).also {
            Log.i("StandardVMicEngine", "Deployed mic handle with format: $pcm16Format")
        }
    }
}

private class StandardVMicHandle(
    private val context: Context,
    override val mission: AudioMission
) : VMicHandle {
    private val patches = CopyOnWriteArrayList<StandardAudioPatch>()
    private var policySession: StandardLoopbackPolicySession? = null
    private val lock = Any()

    internal fun getOrCreatePolicySession(
        uid: Int,
        mission: AudioMission
    ): StandardLoopbackPolicySession {
        synchronized(lock) {
            val current = policySession
            if (current != null && current.captureUid == uid) {
                Log.d(TAG, "Reusing existing policySession for uid=$uid")
                return current
            }
            Log.i(TAG, "Creating new StandardLoopbackPolicySession for uid=$uid")
            current?.close()
            return StandardLoopbackPolicySession(
                context,
                mission,
                captureUid = uid
            ).also { policySession = it }
        }
    }

    override fun sourceApp(uid: Int, presets: IntArray, start: Boolean): AudioPatch {
        synchronized(lock) {
            Log.i(TAG, "sourceApp called for uid=$uid, start=$start")
            val appMission = AudioMission(
                format = mission.format,
                presets = presets,
                timeoutMillis = mission.timeoutMillis
            )
            return StandardAudioPatch(context, uid, appMission, this).also {
                patches.add(it)
                if (start) it.start()
            }
        }
    }

    override fun createManualTrack(): AudioTrack? {
        synchronized(lock) {
            Log.i(TAG, "createManualTrack called")
            if (policySession == null) {
                policySession = StandardLoopbackPolicySession(
                    context,
                    mission,
                    captureUid = -1
                )
            }
            return policySession?.createTrack()
        }
    }

    override fun close() {
        synchronized(lock) {
            Log.i(TAG, "Closing StandardVMicHandle")
            patches.forEach { runCatching { it.close() } }
            patches.clear()
            policySession?.close()
            policySession = null
        }
    }

    companion object {
        private const val TAG = "StandardVMicHandle"
    }
}

private class StandardAudioPatch(
    private val context: Context,
    private val uid: Int,
    private val mission: AudioMission,
    private val handle: StandardVMicHandle
) : AudioPatch {
    companion object {
        private const val TAG = "StandardAudioPatch"
    }

    private var transferJob: PcmTransferJob? = null
    private val lock = Any()

    override fun start() {
        synchronized(lock) {
            Log.i(TAG, "Starting StandardAudioPatch routing for uid=$uid")
            val job = transferJob
            if (job != null) {
                Log.d(TAG, "Resuming existing transfer job for uid=$uid")
                job.resume()
                return
            }

            val session = handle.getOrCreatePolicySession(uid, mission)
            transferJob = PcmTransferJob(
                session,
                session,
                "AudioPatch-$uid"
            ).apply {
                Log.i(TAG, "Starting new PcmTransferJob for uid=$uid")
                start()
            }
        }
    }

    override fun pause(keepResources: Boolean) {
        synchronized(lock) {
            Log.i(
                TAG,
                "Pausing StandardAudioPatch for uid=$uid, keepResources=$keepResources"
            )
            if (keepResources) {
                transferJob?.pause()
            } else {
                transferJob?.stop()
                transferJob = null
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            Log.i(TAG, "Closing StandardAudioPatch for uid=$uid")
            transferJob?.stop()
            transferJob = null
        }
    }
}
