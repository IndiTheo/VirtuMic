package com.indidevs.virtumic.core.internal.mechanism

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.indidevs.virtumic.core.CaptureSession
import com.indidevs.virtumic.core.PlaybackSession
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PcmTransferJobTest {

    private class NullCaptureSession : CaptureSession {
        override fun createRecord(): AudioRecord? = null
        override fun close() {}
    }

    private class NullPlaybackSession : PlaybackSession {
        override fun createTrack(): AudioTrack? = null
        override fun close() {}
    }

    private class ValidCaptureSession : CaptureSession {
        override fun createRecord(): AudioRecord {
            return AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(48000)
                        .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(2048)
                .build()
        }
        override fun close() {}
    }

    private class ValidPlaybackSession : PlaybackSession {
        override fun createTrack(): AudioTrack {
            return AudioTrack.Builder()
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(48000)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(2048)
                .build()
        }
        override fun close() {}
    }

    @Test
    fun `start throws IllegalStateException when capture session returns null record`() {
        val job = PcmTransferJob(
            captureSession = NullCaptureSession(),
            playbackSession = ValidPlaybackSession(),
            threadName = "TestTransferJob"
        )

        assertThrows(IllegalStateException::class.java) {
            job.start()
        }
    }

    @Test
    fun `start throws IllegalStateException when playback session returns null track`() {
        val job = PcmTransferJob(
            captureSession = ValidCaptureSession(),
            playbackSession = NullPlaybackSession(),
            threadName = "TestTransferJob"
        )

        assertThrows(IllegalStateException::class.java) {
            job.start()
        }
    }
}
