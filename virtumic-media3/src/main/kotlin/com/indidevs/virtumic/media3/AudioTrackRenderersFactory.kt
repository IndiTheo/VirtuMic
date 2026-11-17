package com.indidevs.virtumic.media3

import android.content.Context
import android.media.AudioTrack
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import java.nio.ByteBuffer

/**
 * An [AudioProcessor] that discards all incoming PCM audio bytes when active,
 * allowing ExoPlayer's playback clock to run without outputting sound to physical speakers.
 */
@OptIn(UnstableApi::class)
class DiscardingAudioProcessor(
    private val shouldDiscardProvider: () -> Boolean
) : BaseAudioProcessor() {

    override fun isActive(): Boolean = shouldDiscardProvider()

    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat
    ): AudioProcessor.AudioFormat {
        val discard = shouldDiscardProvider()
        Log.d(
            "DiscardingProcessor",
            "Configured DiscardingAudioProcessor: active=$discard, format=$inputAudioFormat"
        )
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        inputBuffer.position(inputBuffer.limit())
    }
}

/**
 * An [AudioProcessor] that dynamically configures [ChannelMixingAudioProcessor]
 * with the appropriate [ChannelMixingMatrix] for channel remapping (e.g., Stereo <-> Mono).
 */
@OptIn(UnstableApi::class)
class DynamicChannelMixingAudioProcessor(
    private val targetChannelCountProvider: () -> Int?,
    private val mixer: ChannelMixingAudioProcessor = ChannelMixingAudioProcessor()
) : AudioProcessor by mixer {

    override fun configure(
        inputAudioFormat: AudioProcessor.AudioFormat
    ): AudioProcessor.AudioFormat {
        val targetChannels = targetChannelCountProvider() ?: inputAudioFormat.channelCount
        val inputChannels = inputAudioFormat.channelCount

        val matrix = when {
            inputChannels == 1 && targetChannels == 1 -> {
                ChannelMixingMatrix(1, 1, floatArrayOf(1.0f))
            }
            inputChannels == 2 && targetChannels == 2 -> {
                ChannelMixingMatrix(2, 2, floatArrayOf(1.0f, 0.0f, 0.0f, 1.0f))
            }
            inputChannels == 1 && targetChannels == 2 -> {
                // Mono to Stereo: L = Mono, R = Mono
                ChannelMixingMatrix(1, 2, floatArrayOf(1.0f, 1.0f))
            }
            inputChannels == 2 && targetChannels == 1 -> {
                // Stereo to Mono: Mono = 0.5*L + 0.5*R
                ChannelMixingMatrix(2, 1, floatArrayOf(0.5f, 0.5f))
            }
            else -> {
                ChannelMixingMatrix(inputChannels, targetChannels, floatArrayOf(1.0f))
            }
        }
        mixer.putChannelMixingMatrix(matrix)

        return mixer.configure(inputAudioFormat)
    }
}

/**
 * An [AudioProcessor] that dynamically updates [SonicAudioProcessor]'s output sample rate
 * when ExoPlayer configures its AudioSink, enabling dynamic software resampling.
 */
@OptIn(UnstableApi::class)
class DynamicResamplingAudioProcessor(
    private val targetSampleRateProvider: () -> Int?,
    private val sonic: SonicAudioProcessor = SonicAudioProcessor()
) : AudioProcessor by sonic {

    override fun configure(
        inputAudioFormat: AudioProcessor.AudioFormat
    ): AudioProcessor.AudioFormat {
        val targetRate = targetSampleRateProvider()
        if (targetRate != null && targetRate > 0 && targetRate != inputAudioFormat.sampleRate) {
            Log.i(
                "DynamicResampler",
                "Configuring Sonic audio resampler: input=${inputAudioFormat.sampleRate}Hz -> target=${targetRate}Hz"
            )
            sonic.setOutputSampleRateHz(targetRate)
        } else {
            Log.d(
                "DynamicResampler",
                "Passthrough mode (no resampling required for ${inputAudioFormat.sampleRate}Hz)"
            )
            sonic.setOutputSampleRateHz(androidx.media3.common.Format.NO_VALUE)
        }
        return sonic.configure(inputAudioFormat)
    }
}

/**
 * A custom RenderersFactory that injects a pre-configured AudioTrack into ExoPlayer.
 */
@OptIn(UnstableApi::class)
class AudioTrackRenderersFactory(
    context: Context,
    private val targetFormatProvider: () -> android.media.AudioFormat? = { null },
    private val shouldDiscardAudioProvider: () -> Boolean = { false },
    private val trackProvider: () -> AudioTrack?
) : DefaultRenderersFactory(context) {

    internal fun provideAudioTrack(): AudioTrack {
        return trackProvider()
            ?: throw IllegalStateException(
                "Failed to create virtual microphone injection AudioTrack. Local speaker fallback is prohibited."
            )
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink {
        val discardingProcessor = DiscardingAudioProcessor(
            shouldDiscardProvider = shouldDiscardAudioProvider
        )
        val channelMixer = DynamicChannelMixingAudioProcessor(
            targetChannelCountProvider = { targetFormatProvider()?.channelCount }
        )
        val dynamicResampler = DynamicResamplingAudioProcessor(
            targetSampleRateProvider = { targetFormatProvider()?.sampleRate }
        )

        val customProvider =
            DefaultAudioSink.AudioTrackProvider { _, _, _ ->
                provideAudioTrack()
            }

        return DefaultAudioSink.Builder(context)
            .setAudioTrackProvider(customProvider)
            .setAudioProcessors(arrayOf(discardingProcessor, channelMixer, dynamicResampler))
            .build()
    }
}
