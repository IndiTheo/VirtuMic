package com.indidevs.virtumic.core.internal.platform.policy

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.audiopolicy.AudioMix
import android.media.audiopolicy.AudioMixingRule
import android.media.audiopolicy.AudioPolicy
import android.os.Build
import android.os.Looper
import android.util.Log
import com.indidevs.virtumic.core.AudioMission
import com.indidevs.virtumic.core.CaptureSession
import com.indidevs.virtumic.core.PlaybackSession

internal class StandardLoopbackPolicySession(
    context: Context,
    val mission: AudioMission,
    val captureUid: Int = -1
) : PlaybackSession, CaptureSession {

    companion object {
        private const val TAG = "StandardLoopbackPolicy"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val policy: AudioPolicy
    private val injectionMix: AudioMix
    private val captureMix: AudioMix?

    init {
        Log.i(
            TAG,
            "Initializing StandardLoopbackPolicySession for captureUid=$captureUid, format=${mission.format}"
        )
        val policyBuilder = AudioPolicy.Builder(context).setLooper(Looper.getMainLooper())

        val injectionRuleBuilder = AudioMixingRule.Builder()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            injectionRuleBuilder.setTargetMixRole(AudioMixingRule.MIX_ROLE_INJECTOR)
        }
        mission.presets.forEach { preset ->
            injectionRuleBuilder.addMixRule(
                AudioMixingRule.RULE_MATCH_ATTRIBUTE_CAPTURE_PRESET,
                AudioAttributes.Builder().setCapturePreset(preset).build()
            )
        }
        injectionMix = AudioMix.Builder(injectionRuleBuilder.build())
            .setFormat(mission.format)
            .setRouteFlags(AudioMix.ROUTE_FLAG_LOOP_BACK)
            .build()
        policyBuilder.addMix(injectionMix)

        if (captureUid != -1) {
            val captureRuleBuilder = AudioMixingRule.Builder()
                .addMixRule(AudioMixingRule.RULE_MATCH_UID, captureUid)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                captureRuleBuilder.setTargetMixRole(AudioMixingRule.MIX_ROLE_PLAYERS)
            }

            // REMOTE_SUBMIX capture AudioRecord strictly requires 16-bit PCM integer
            val captureFormat = AudioFormat.Builder()
                .setSampleRate(mission.format.sampleRate)
                .setChannelMask(mission.format.channelMask)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            val mix = AudioMix.Builder(captureRuleBuilder.build())
                .setFormat(captureFormat)
                .setRouteFlags(AudioMix.ROUTE_FLAG_LOOP_BACK)
                .build()
            policyBuilder.addMix(mix)
            captureMix = mix
        } else {
            captureMix = null
        }

        policy = policyBuilder.build()
        val result = audioManager.registerAudioPolicy(policy)
        if (result != 0) {
            Log.e(TAG, "Failed to register audio policy: $result")
            throw IllegalStateException("Failed to register unified loopback audio policy: $result")
        }
        Log.i(TAG, "Registered unified loopback AudioPolicy for captureUid=$captureUid")
    }

    override fun createTrack(): AudioTrack? = try {
        Log.i(TAG, "Creating injection AudioTrack source...")
        policy.createAudioTrackSource(injectionMix).also {
            Log.i(
                TAG,
                "Injection AudioTrack created successfully (sessionId=${it.audioSessionId})"
            )
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to create injection AudioTrack", e)
        null
    }

    override fun createRecord(): AudioRecord? {
        val mix = captureMix ?: return null
        return try {
            Log.i(TAG, "Creating capture AudioRecord sink for captureUid=$captureUid...")
            policy.createAudioRecordSink(mix).also {
                Log.i(TAG, "Capture AudioRecord created successfully (state=${it.state})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create capture AudioRecord", e)
            null
        }
    }

    override fun close() {
        Log.i(TAG, "Closing StandardLoopbackPolicySession for captureUid=$captureUid")
        runCatching { audioManager.unregisterAudioPolicy(policy) }
    }
}
