package com.indidevs.virtumic.service.session

import android.media.AudioTrack
import android.util.Log
import androidx.media3.session.MediaSession
import com.indidevs.virtumic.core.AudioMission
import com.indidevs.virtumic.core.AudioPatch
import com.indidevs.virtumic.core.VMicEngine
import com.indidevs.virtumic.core.VMicHandle
import java.util.concurrent.ConcurrentHashMap

/**
 * Orchestrates media sessions, virtual microphone handles, and app capture audio patches
 * for the `virtumic-service` layer.
 *
 * This orchestrator manages active virtual microphones, coordinates [VMicEngine] bindings,
 * and maintains the lifecycle of virtual audio injection tracks and capture policies.
 *
 * ## Concurrency & Threading Model
 * This component is fully thread-safe and designed to coordinate state across multiple execution domains:
 * - **Main Thread:** [MediaSession] service callbacks, `MediaItem` transitions, and player
 *   lifecycle commands (`play`, `pause`, `stop`) execute sequentially.
 * - **ExoPlayer Audio / Render Threads:** High-frequency audio sink factories and format
 *   providers query session state from background audio threads.
 *
 * **Synchronization Strategies:**
 * - **Session Registry:** Backed by a thread-safe [ConcurrentHashMap] (`sessions`) with atomic
 *   initialization via `computeIfAbsent`.
 * - **Per-Session Monitor Locks:** Each isolated `Session` instance acts as its own monitor lock
 *   (`synchronized(s)`). All state mutations—including microphone handle deployment ([deployMic]), patch
 *   connections ([sourceApp]), session binding ([setSession]), and teardown procedures
 *   ([stopStreaming], [releaseSession])—are synchronized.
 * - **Lock-Free Volatile Reads:** High-frequency queries from background ExoPlayer audio threads
 *   (`getHandle`) read `@Volatile` fields (`micHandle`) lock-free.
 *
 * ## Resource Lifecycle
 * Callers must invoke `releaseSession(micId)` or `releaseAll()` during service teardown to
 * unregister system `AudioPolicy` instances, close active [AudioPatch] handles, and release [MediaSession]s.
 *
 * @property engineProvider a provider lambda supplying the [VMicEngine] instance.
 */
internal class VMicSessionOrchestrator(private val engineProvider: () -> VMicEngine?) {
    private class Session(
        var mediaSession: MediaSession? = null,
        @Volatile var micHandle: VMicHandle? = null,
        val patches: ConcurrentHashMap<Int, AudioPatch> = ConcurrentHashMap(),
        var capturedAppUid: Int = -1,
        var targetPresets: IntArray? = null
    ) {
        fun clearPatches() {
            patches.values.forEach { it.close() }
            patches.clear()
        }

        fun closeMicHandle() {
            clearPatches()
            micHandle?.close()
            micHandle = null
        }
    }

    private val sessions = ConcurrentHashMap<String, Session>()

    private fun getOrCreateSession(micId: String): Session {
        return sessions.computeIfAbsent(micId) { Session() }
    }

    fun getSession(id: String) = sessions[id]?.mediaSession
    fun setSession(id: String, s: MediaSession) {
        getOrCreateSession(id).mediaSession = s
    }

    /** Deploys or reconfigures the mic handle for a session if the mission changes. */
    fun deployMic(micId: String, mission: AudioMission) {
        val s = getOrCreateSession(micId)
        synchronized(s) {
            val currentHandle = s.micHandle
            if (currentHandle != null && currentHandle.mission == mission) return
            if (currentHandle != null) {
                Log.i(
                    TAG,
                    "Reconfiguring mic handle for $micId from ${currentHandle.format} to ${mission.format}"
                )
                s.closeMicHandle()
            }
            s.micHandle = engineProvider()?.deployMic(mission)
                ?: throw IllegalStateException(
                    "Failed to deploy virtual microphone handle for $micId at ${mission.format}"
                )
        }
    }

    fun getHandle(micId: String): VMicHandle? = sessions[micId]?.micHandle

    private fun requireHandle(micId: String, s: Session): VMicHandle {
        return s.micHandle ?: throw IllegalStateException(
            "Virtual mic handle was unavailable for mic $micId"
        )
    }

    fun createPlayerInjectionTrack(micId: String): AudioTrack? {
        val s = getOrCreateSession(micId)
        synchronized(s) {
            Log.d(
                TAG,
                "File streaming mode for $micId: creating manual injection AudioTrack"
            )
            val handle = requireHandle(micId, s)
            return handle.createManualTrack()
        }
    }

    /**
     * Sources audio from a target app into the virtual mic. Replaces any previous patch on that UID.
     * Stores recorded app UID and presets to ensure atomic start upon play().
     */
    fun sourceApp(
        micId: String,
        capturedAppUid: Int,
        targetPresets: IntArray?,
        isPlaying: Boolean
    ) {
        val s = getOrCreateSession(micId)
        synchronized(s) {
            s.capturedAppUid = capturedAppUid
            s.targetPresets = targetPresets

            val handle = requireHandle(micId, s)
            if (s.patches.containsKey(capturedAppUid)) {
                Log.d(TAG, "Patch for UID $capturedAppUid in mic $micId already active")
                if (isPlaying) {
                    s.patches[capturedAppUid]?.start()
                }
                return
            }
            val presets = targetPresets ?: com.indidevs.virtumic.core.AudioMission.DEFAULT_PRESETS
            s.clearPatches()
            s.patches[capturedAppUid] = handle.sourceApp(capturedAppUid, presets, isPlaying)
        }
    }

    fun startPatches(micId: String) {
        val s = sessions[micId] ?: return
        synchronized(s) {
            if (s.patches.isEmpty() && s.capturedAppUid > -1) {
                sourceApp(micId, s.capturedAppUid, s.targetPresets, true)
            } else {
                s.patches.values.forEach { it.start() }
            }
        }
    }

    fun pausePatches(micId: String) {
        val s = sessions[micId] ?: return
        synchronized(s) {
            s.patches.values.forEach { it.pause() }
        }
    }

    /** Stops streaming and tears down the mic handle and audio policy immediately. */
    fun stopStreaming(micId: String) {
        val s = sessions[micId] ?: return
        synchronized(s) {
            s.closeMicHandle()
            s.capturedAppUid = -1
            s.targetPresets = null
        }
    }

    /** Releases session resources and removes the session entry from the map. */
    fun releaseSession(micId: String) {
        val s = sessions.remove(micId) ?: return
        synchronized(s) {
            s.closeMicHandle()
            s.mediaSession?.release()
            s.mediaSession = null
        }
    }

    fun releaseAll() {
        sessions.keys.toList().forEach { releaseSession(it) }
    }

    companion object {
        private const val TAG = "VMicSessionOrchestrator"
    }
}
