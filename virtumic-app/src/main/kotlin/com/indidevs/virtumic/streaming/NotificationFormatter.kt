package com.indidevs.virtumic.streaming

import android.media.AudioFormat
import android.text.Html
import com.indidevs.virtumic.domain.model.AppSourceInfo
import com.indidevs.virtumic.domain.model.FileSourceInfo
import com.indidevs.virtumic.domain.repository.NotifField
import com.indidevs.virtumic.domain.repository.NotifFieldConfig
import com.indidevs.virtumic.domain.repository.NotifLineConfig

fun AudioFormat.formatToString(): String {
    val rateStr = if (sampleRate >= 1000) "${sampleRate / 1000}kHz" else "${sampleRate}Hz"
    val channelStr = when (channelMask) {
        AudioFormat.CHANNEL_OUT_STEREO -> "Stereo"
        AudioFormat.CHANNEL_OUT_MONO -> "Mono"
        else -> "${Integer.bitCount(channelMask)}ch"
    }
    return "$rateStr $channelStr"
}

object NotificationFormatter {

    fun formatTitle(
        lineConfig: NotifLineConfig,
        fileInfo: FileSourceInfo?,
        appInfo: AppSourceInfo?,
        micFormat: AudioFormat,
        otherLineConfig: NotifLineConfig? = null
    ): String = buildLineText(lineConfig, fileInfo, appInfo, micFormat)

    fun formatArtist(
        lineConfig: NotifLineConfig,
        fileInfo: FileSourceInfo?,
        appInfo: AppSourceInfo?,
        micFormat: AudioFormat,
        otherLineConfig: NotifLineConfig? = null
    ): String = buildLineText(lineConfig, fileInfo, appInfo, micFormat)

    private fun resolveFieldValue(
        fieldConfig: NotifFieldConfig,
        fileInfo: FileSourceInfo?,
        appInfo: AppSourceInfo?,
        micFormat: AudioFormat
    ): String {
        val field = fieldConfig.field
        val rawValue = getRawFieldValue(field, fileInfo, appInfo, micFormat)
        if (rawValue.isNotBlank()) {
            return rawValue
        }

        if (fieldConfig.useFallback &&
            fieldConfig.fallbackField != NotifField.NONE &&
            fieldConfig.fallbackField != field
        ) {
            return resolveFieldValue(
                NotifFieldConfig(fieldConfig.fallbackField),
                fileInfo,
                appInfo,
                micFormat
            )
        }

        return field.unknownPlaceholder ?: ""
    }

    private fun getRawFieldValue(
        field: NotifField,
        fileInfo: FileSourceInfo?,
        appInfo: AppSourceInfo?,
        micFormat: AudioFormat
    ): String {
        return when (field) {
            NotifField.NONE -> ""
            NotifField.FILE_NAME -> fileInfo?.fileName?.unescapeHtml() ?: ""
            NotifField.ID3_TITLE -> fileInfo?.title?.unescapeHtml() ?: ""
            NotifField.ID3_ARTIST -> fileInfo?.artist?.unescapeHtml() ?: ""
            NotifField.ALBUM -> fileInfo?.album?.unescapeHtml() ?: ""
            NotifField.APP_NAME -> appInfo?.appName?.unescapeHtml() ?: ""
            NotifField.PACKAGE_NAME -> appInfo?.packageName ?: ""
            NotifField.FULL_AUDIO_FORMAT -> micFormat.formatToString()
            NotifField.SAMPLE_RATE -> {
                val rate = micFormat.sampleRate
                if (rate >= 1000) "${rate / 1000}kHz" else "${rate}Hz"
            }
            NotifField.CHANNELS -> {
                when (micFormat.channelMask) {
                    AudioFormat.CHANNEL_OUT_STEREO -> "Stereo"
                    AudioFormat.CHANNEL_OUT_MONO -> "Mono"
                    else -> "${Integer.bitCount(micFormat.channelMask)}ch"
                }
            }
        }
    }

    private fun buildLineText(
        lineConfig: NotifLineConfig,
        fileInfo: FileSourceInfo?,
        appInfo: AppSourceInfo?,
        micFormat: AudioFormat
    ): String {
        val primaryVal = resolveFieldValue(lineConfig.primaryConfig, fileInfo, appInfo, micFormat)
        val appendableVal =
            resolveFieldValue(lineConfig.appendableConfig, fileInfo, appInfo, micFormat)

        return if (appendableVal.isNotBlank() && primaryVal != appendableVal) {
            "$primaryVal ${lineConfig.separator.symbol} $appendableVal"
        } else {
            primaryVal
        }
    }

    private fun String?.unescapeHtml(): String? {
        if (this == null) return null
        return try {
            Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY).toString()
        } catch (_: Exception) {
            this
        }
    }
}
