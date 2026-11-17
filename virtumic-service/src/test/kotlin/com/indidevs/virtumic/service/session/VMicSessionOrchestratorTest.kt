package com.indidevs.virtumic.service.session

import android.media.AudioFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.indidevs.virtumic.core.AudioMission
import com.indidevs.virtumic.core.AudioPatch
import com.indidevs.virtumic.core.VMicEngine
import com.indidevs.virtumic.core.VMicHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VMicSessionOrchestratorTest {

    private class FakeHandle(override val mission: AudioMission) : VMicHandle {
        var closed = false
        override fun sourceApp(uid: Int, presets: IntArray, start: Boolean): AudioPatch {
            TODO("Not yet implemented")
        }
        override fun createManualTrack() = null
        override fun close() {
            closed = true
        }
    }

    private class FakeEngine : VMicEngine {
        var deployCount = 0
        var lastMission: AudioMission? = null

        override fun deployMic(mission: AudioMission): VMicHandle {
            deployCount++
            lastMission = mission
            return FakeHandle(mission)
        }
    }

    private val orchestrator = VMicSessionOrchestrator { null }

    @Test
    fun `throws when direct playback handle is unavailable`() {
        val micId = "test_mic"

        // When mic handle is null, an indicative error should be thrown - unexpected state
        assertThrows(IllegalStateException::class.java) {
            orchestrator.createPlayerInjectionTrack(micId)
        }
    }

    @Test
    fun `deployMic deploys new handle when none exists`() {
        val engine = FakeEngine()
        val orchestrator = VMicSessionOrchestrator { engine }
        val micId = "mic_1"
        val format = AudioFormat.Builder().setSampleRate(48000).build()
        val mission = AudioMission(format)

        orchestrator.deployMic(micId, mission)

        assertEquals(1, engine.deployCount)
        assertEquals(mission, orchestrator.getHandle(micId)?.mission)
    }

    @Test
    fun `deployMic is no-op when same mission is deployed twice`() {
        val engine = FakeEngine()
        val orchestrator = VMicSessionOrchestrator { engine }
        val micId = "mic_1"
        val format = AudioFormat.Builder().setSampleRate(48000).build()
        val mission = AudioMission(format)

        orchestrator.deployMic(micId, mission)
        val handle1 = orchestrator.getHandle(micId)

        // Deploy same mission again
        orchestrator.deployMic(micId, mission)
        val handle2 = orchestrator.getHandle(micId)

        assertEquals(1, engine.deployCount) // Did not re-deploy
        assertEquals(handle1, handle2)
    }

    @Test
    fun `deployMic reconfigures handle when format changes`() {
        val engine = FakeEngine()
        val orchestrator = VMicSessionOrchestrator { engine }
        val micId = "mic_1"
        val format1 = AudioFormat.Builder().setSampleRate(48000).build()
        val format2 = AudioFormat.Builder().setSampleRate(44100).build()
        val mission1 = AudioMission(format1, intArrayOf(1))
        val mission2 = AudioMission(format2, intArrayOf(1))

        orchestrator.deployMic(micId, mission1)
        val handle1 = orchestrator.getHandle(micId) as FakeHandle

        orchestrator.deployMic(micId, mission2)
        val handle2 = orchestrator.getHandle(micId) as FakeHandle

        assertEquals(2, engine.deployCount)
        assertEquals(true, handle1.closed)
        assertEquals(false, handle2.closed)
        assertEquals(mission2, handle2.mission)
    }

    @Test
    fun `deployMic reconfigures handle when presets change`() {
        val engine = FakeEngine()
        val orchestrator = VMicSessionOrchestrator { engine }
        val micId = "mic_1"
        val format = AudioFormat.Builder().setSampleRate(48000).build()
        val mission1 = AudioMission(format, intArrayOf(1))
        val mission2 = AudioMission(format, intArrayOf(2))

        orchestrator.deployMic(micId, mission1)
        val handle1 = orchestrator.getHandle(micId) as FakeHandle

        orchestrator.deployMic(micId, mission2)
        val handle2 = orchestrator.getHandle(micId) as FakeHandle

        assertEquals(2, engine.deployCount)
        assertEquals(true, handle1.closed)
        assertEquals(false, handle2.closed)
        assertEquals(mission2, handle2.mission)
    }

    @Test
    fun `stopStreaming closes mic handle and cleans up session on error recovery`() {
        val engine = FakeEngine()
        val orchestrator = VMicSessionOrchestrator { engine }
        val micId = "mic_error"
        val format = AudioFormat.Builder().setSampleRate(48000).build()
        val mission = AudioMission(format)

        orchestrator.deployMic(micId, mission)
        val handle = orchestrator.getHandle(micId) as FakeHandle

        // Simulate error recovery stopping stream
        orchestrator.stopStreaming(micId)

        assertEquals(true, handle.closed)
        assertEquals(null, orchestrator.getHandle(micId))
    }

    @Test
    fun `deployMic throws IllegalStateException when engine provider returns null handle`() {
        val orchestrator = VMicSessionOrchestrator {
            object : VMicEngine {
                override fun deployMic(mission: AudioMission): VMicHandle {
                    throw IllegalStateException("Engine failed to deploy")
                }
            }
        }
        val micId = "mic_null_engine"
        val format = AudioFormat.Builder().setSampleRate(48000).build()
        val mission = AudioMission(format)

        assertThrows(IllegalStateException::class.java) {
            orchestrator.deployMic(micId, mission)
        }
    }
}
