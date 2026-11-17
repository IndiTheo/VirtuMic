package com.indidevs.virtumic.core.internal.mechanism

import android.media.AudioRecord
import android.media.AudioTrack
import android.util.Log
import com.indidevs.virtumic.core.CaptureSession
import com.indidevs.virtumic.core.PlaybackSession
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

/**
 * A background worker thread that transfers raw PCM audio bytes
 * between a [CaptureSession] and a [PlaybackSession].
 *
 * Manages the complete lifecycle of the underlying [AudioRecord] and [AudioTrack]
 * hardware instances and enforces a thread-safe state machine with explicit
 * error recovery paths to [STOPPED].
 *
 * ### Concurrency & Thread Safety
 * * **Thread Model:** Allocates a single dedicated background thread (`"PcmTransferJob"`) for continuous PCM transfer.
 * * **Synchronization:** All state transitions and hardware mutations are guarded by a [ReentrantLock].
 * * **Thread Suspension:** Pausing suspends the worker thread via a [Condition] variable without destroying the thread.
 *
 * ### State Machine Diagram
 * ```
 *                        +---------+
 *         +------------> | STOPPED | <-------------------+
 *         |              +---------+                     |
 *         |                   |                          |
 *         |                   | start()                  | stop() /
 *         |                   v                          | Error /
 *         |              +---------+                     | Failure
 *         +------------> | RUNNING | --------------------+
 *         |              +---------+                     |
 *         |                   |                          |
 *         | resume()          | pause()                  |
 *         |                   v                          |
 *         +------------- +---------+                     |
 *                        | PAUSED  | --------------------+
 *                        +---------+
 *
 * @param captureSession The session producing captured PCM audio bytes.
 * @param playbackSession The session consuming injected PCM audio bytes.
 * @param threadName The descriptive name for the background transfer thread.
 */
internal class PcmTransferJob(
    private val captureSession: CaptureSession,
    private val playbackSession: PlaybackSession,
    private val threadName: String = "PcmTransferJob"
) {
    private enum class State { RUNNING, PAUSED, STOPPED }

    companion object {
        private const val TAG = "PcmTransferJob"
        private const val DEFAULT_BUFFER_SIZE = 2048
    }

    private val lock = ReentrantLock()
    private val condition = lock.newCondition()
    private var state = State.STOPPED

    private var currentRecord: AudioRecord? = null
    private var currentTrack: AudioTrack? = null

    /**
     * Starts the transfer thread.
     */
    fun start() {
        lock.withLock {
            if (state != State.STOPPED) return

            val record = captureSession.createRecord()
            val track = playbackSession.createTrack()

            if (record == null) {
                Log.e(TAG, "Failed to start: AudioRecord (capture sink) is null")
                track?.release()
                throw IllegalStateException(
                    "Failed to create AudioRecord capture sink for transfer job '$threadName'"
                )
            }
            if (track == null) {
                Log.e(TAG, "Failed to start: AudioTrack (injection source) is null")
                record.release()
                throw IllegalStateException(
                    "Failed to create AudioTrack injection source for transfer job '$threadName'"
                )
            }

            try {
                record.startRecording()
                track.play()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize audio hardware for transfer", e)
                record.release()
                track.release()
                throw IllegalStateException(
                    "Failed to start audio hardware for transfer job '$threadName'",
                    e
                )
            }

            currentRecord = record
            currentTrack = track
            state = State.RUNNING

            thread(name = threadName) {
                runTransferLoop(record, track)
            }
        }
    }

    private fun runTransferLoop(record: AudioRecord, track: AudioTrack) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        try {
            while (true) {
                lock.withLock {
                    while (state == State.PAUSED) {
                        condition.await()
                    }
                    if (state == State.STOPPED) return@runTransferLoop
                }

                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    track.write(buffer, 0, read)
                } else {
                    if (read < 0) {
                        Log.w(TAG, "AudioRecord read error: $read")
                    }
                    Thread.sleep(10)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during PCM transfer loop", e)
        } finally {
            cleanup()
            Log.d(TAG, "Transfer job '$threadName' loop exited")
        }
    }

    private fun cleanup() {
        lock.withLock {
            currentRecord?.apply {
                try {
                    stop()
                    release()
                } catch (_: Exception) {}
            }
            currentTrack?.apply {
                try {
                    stop()
                    release()
                } catch (_: Exception) {}
            }
            currentRecord = null
            currentTrack = null
            state = State.STOPPED
        }
    }

    /**
     * Stops the transfer thread and releases resources.
     */
    fun stop() {
        lock.withLock {
            state = State.STOPPED
            condition.signalAll()
            // Cleanup will be called by the thread, but we can speed it up
            currentRecord?.tryStop()
            currentTrack?.tryStop()
        }
    }

    /**
     * Pauses the transfer loop and stops hardware to release focus.
     */
    fun pause() {
        lock.withLock {
            if (state == State.RUNNING) {
                state = State.PAUSED
                currentRecord?.tryStop()
                currentTrack?.tryPause()
            }
        }
    }

    /**
     * Resumes the transfer loop and restarts hardware.
     */
    fun resume() {
        lock.withLock {
            if (state == State.PAUSED) {
                try {
                    currentRecord?.startRecording()
                    currentTrack?.play()
                    state = State.RUNNING
                    condition.signalAll()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to resume audio hardware", e)
                    state = State.STOPPED
                    condition.signalAll()
                    throw IllegalStateException(
                        "Failed to resume audio hardware for transfer job '$threadName'",
                        e
                    )
                }
            }
        }
    }

    private fun AudioRecord.tryStop() {
        try {
            stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping record", e)
        }
    }

    private fun AudioTrack.tryStop() {
        try {
            stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping track", e)
        }
    }

    private fun AudioTrack.tryPause() {
        try {
            pause()
        } catch (e: Exception) {
            Log.w(TAG, "Error pausing track", e)
        }
    }
}
