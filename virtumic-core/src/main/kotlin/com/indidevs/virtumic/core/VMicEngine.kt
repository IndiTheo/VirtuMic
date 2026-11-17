package com.indidevs.virtumic.core

import android.content.Context
import android.media.AudioFormat
import android.media.AudioTrack
import com.indidevs.virtumic.core.internal.platform.StandardVMicEngine

/**
 * Factory function to create a [VMicEngine] instance.
 */
fun VMicEngine(context: Context): VMicEngine = StandardVMicEngine(context)

/**
 * The primary entry point for the VirtuMic Core Library.
 */
interface VMicEngine {
    /**
     * Deploys a stable Virtual Microphone device.
     */
    fun deployMic(mission: AudioMission): VMicHandle
}

/**
 * A handle to a stable Virtual Microphone device.
 */
interface VMicHandle : AutoCloseable {
    /** The active audio mission parameters governing this virtual microphone. */
    val mission: AudioMission

    /** The active audio format governing this virtual microphone. */
    val format: AudioFormat get() = mission.format

    /**
     * Patches an app's output into this microphone automatically.
     * @param start If true, immediately starts the audio flow.
     */
    fun sourceApp(uid: Int, presets: IntArray, start: Boolean = false): AudioPatch

    /**
     * Creates an [AudioTrack] for manual PCM injection (e.g. Files).
     */
    fun createManualTrack(): AudioTrack?

    override fun close()
}

/**
 * A handle to an active source-to-mic audio patch.
 */
interface AudioPatch : AutoCloseable {
    /**
     * Starts or resumes the PCM pump.
     */
    fun start()

    /**
     * Halts the PCM pump.
     * @param keepResources If true, keeps hardware handles for instant resume.
     *                      If false, releases hardware and kills the pump thread for deep power saving.
     */
    fun pause(keepResources: Boolean = true)

    /**
     * Unregisters the interception and stops the pump entirely.
     */
    override fun close()
}
