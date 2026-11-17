package com.indidevs.virtumic.streaming

import android.content.Context
import android.net.Uri
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.indidevs.virtumic.domain.model.AppSourceInfo
import com.indidevs.virtumic.domain.model.AudioSource
import com.indidevs.virtumic.domain.model.FileSourceInfo
import com.indidevs.virtumic.service.session.VMicMediaSessionService
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StreamingJobTest {

    private val testFileInfo = FileSourceInfo(
        uri = Uri.parse("file:///test.wav"),
        fileName = "test.wav",
        format = "wav",
        duration = Duration.ofSeconds(10),
        sampleRate = 44100,
        channelCount = 2
    )

    private val testAudioSource = AudioSource.File(testFileInfo)

    @Test
    fun `playerListener triggers onJobError when onPlayerError is called`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        var capturedError: Throwable? = null
        val job = StreamingJob(
            context = context,
            source = testAudioSource,
            serviceClass = VMicMediaSessionService::class.java,
            onJobError = { error ->
                capturedError = error
            }
        )

        val testException = PlaybackException(
            null,
            RuntimeException("Service crashed"),
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
        )
        job.playerListener.onPlayerError(testException)

        assertEquals(testException, capturedError)
    }

    @Test
    fun `playerListener triggers onJobCompleted when playback state ends`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        var completed = false
        val job = StreamingJob(
            context = context,
            source = testAudioSource,
            serviceClass = VMicMediaSessionService::class.java,
            onJobCompleted = {
                completed = true
            }
        )

        job.playerListener.onPlaybackStateChanged(Player.STATE_ENDED)

        assertTrue(completed)
    }

    @Test
    fun `unsupported source type throws exception on start`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val unsupportedSource = AudioSource.None("test")
        val job = StreamingJob(
            context = context,
            source = unsupportedSource,
            serviceClass = VMicMediaSessionService::class.java
        )

        var thrownException: Throwable? = null
        kotlinx.coroutines.runBlocking {
            try {
                job.start(false)
            } catch (e: Exception) {
                thrownException = e
            }
        }

        org.junit.Assert.assertNotNull(thrownException)
        org.junit.Assert.assertTrue(thrownException is IllegalArgumentException)
    }

    @Test
    fun `start with invalid service class explicitly throws`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val sources = listOf(
            testAudioSource,
            AudioSource.App(
                AppSourceInfo(
                    appName = "Test App",
                    description = "Test Description",
                    packageName = "com.example.app"
                )
            )
        )

        for (source in sources) {
            val job = StreamingJob(
                context = context,
                source = source,
                serviceClass = String::class.java
            )

            var thrownException: Throwable? = null
            kotlinx.coroutines.runBlocking {
                try {
                    job.start(false)
                } catch (e: Exception) {
                    thrownException = e
                }
            }

            org.junit.Assert.assertNotNull(
                "Expected start() to throw for source: ${source::class.simpleName}",
                thrownException
            )
        }
    }

    @Test
    fun `stop on unstarted job executes without error`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val job = StreamingJob(
            context = context,
            source = testAudioSource,
            serviceClass = VMicMediaSessionService::class.java
        )

        job.stop()
        // Verifies stop doesn't throw
    }
}
