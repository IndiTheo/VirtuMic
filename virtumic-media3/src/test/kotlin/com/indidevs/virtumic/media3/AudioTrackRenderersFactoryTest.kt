package com.indidevs.virtumic.media3

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioTrackRenderersFactoryTest {

    @Test
    fun `provideAudioTrack throws IllegalStateException when trackProvider returns null`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val factory = AudioTrackRenderersFactory(
            context = context,
            trackProvider = { null }
        )

        assertThrows(IllegalStateException::class.java) {
            factory.provideAudioTrack()
        }
    }
}
