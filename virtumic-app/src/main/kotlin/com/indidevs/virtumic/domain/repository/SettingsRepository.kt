package com.indidevs.virtumic.domain.repository

import android.net.Uri
import com.indidevs.virtumic.domain.model.AppSourceInfo
import com.indidevs.virtumic.domain.model.SourceType

enum class NotifField(
    val displayName: String,
    val unknownPlaceholder: String? = null
) {
    NONE("None"),
    FILE_NAME("File name"),
    FULL_AUDIO_FORMAT("Full audio format"),
    SAMPLE_RATE("Sample rate"),
    CHANNELS("Channels"),
    ID3_TITLE("Title (metadata)", unknownPlaceholder = "Unknown Title"),
    ID3_ARTIST("Artist (metadata)", unknownPlaceholder = "Unknown Artist"),
    ALBUM("Album", unknownPlaceholder = "Unknown Album"),
    APP_NAME("App name"),
    PACKAGE_NAME("Package name")
}

object NotifFieldRules {
    private val MISSINGABLE_FIELDS = setOf(
        NotifField.ID3_TITLE,
        NotifField.ID3_ARTIST,
        NotifField.ALBUM
    )

    fun canBeMissing(field: NotifField): Boolean = field in MISSINGABLE_FIELDS

    fun getDefaultFallback(field: NotifField): NotifField? = when (field) {
        NotifField.ID3_TITLE -> NotifField.FILE_NAME
        NotifField.ID3_ARTIST -> NotifField.FULL_AUDIO_FORMAT
        NotifField.ALBUM -> NotifField.FILE_NAME
        else -> null
    }

    fun isFallbackable(field: NotifField): Boolean = canBeMissing(field)
}

enum class NotifSeparator(val symbol: String) {
    DOT("•"),
    DASH("-"),
    PIPE("|")
}

data class NotifFieldConfig(
    val field: NotifField = NotifField.NONE,
    val useFallback: Boolean = (NotifFieldRules.getDefaultFallback(field) != null),
    val fallbackField: NotifField = NotifFieldRules.getDefaultFallback(field) ?: NotifField.NONE
)

data class NotifLineConfig(
    val primaryConfig: NotifFieldConfig,
    val appendableConfig: NotifFieldConfig = NotifFieldConfig(NotifField.NONE),
    val separator: NotifSeparator = NotifSeparator.DOT
) {
    constructor(
        primary: NotifField,
        appendable: NotifField = NotifField.NONE,
        separator: NotifSeparator = NotifSeparator.DOT
    ) : this(
        primaryConfig = NotifFieldConfig(primary),
        appendableConfig = NotifFieldConfig(appendable),
        separator = separator
    )

    val primary: NotifField get() = primaryConfig.field
    val appendable: NotifField get() = appendableConfig.field
}

data class NotificationCustomizationConfig(
    val fileTitle: NotifLineConfig = DEFAULT_FILE_TITLE,
    val fileArtist: NotifLineConfig = DEFAULT_FILE_ARTIST,
    val appTitle: NotifLineConfig = DEFAULT_APP_TITLE,
    val appArtist: NotifLineConfig = DEFAULT_APP_ARTIST
) {
    companion object {
        val DEFAULT_FILE_TITLE = NotifLineConfig(NotifField.ID3_TITLE)
        val DEFAULT_FILE_ARTIST = NotifLineConfig(NotifField.ID3_ARTIST)
        val DEFAULT_APP_TITLE = NotifLineConfig(NotifField.APP_NAME)
        val DEFAULT_APP_ARTIST = NotifLineConfig(NotifField.FULL_AUDIO_FORMAT)
    }
}

interface SettingsRepository {
    companion object {
        const val DEFAULT_SAMPLE_RATE = 48000
        const val DEFAULT_CHANNEL_MASK = android.media.AudioFormat.CHANNEL_OUT_STEREO
        const val DEFAULT_ENCODING = android.media.AudioFormat.ENCODING_PCM_16BIT
    }

    fun getSelectedSourceType(): SourceType
    fun setSelectedSourceType(type: SourceType)

    fun getSelectedFileUri(): Uri?
    fun setSelectedFileUri(uri: Uri?)

    fun getSelectedAppInfo(): AppSourceInfo?
    fun setSelectedAppInfo(info: AppSourceInfo?)

    fun getPrivilegeMode(): String
    fun setPrivilegeMode(mode: String)

    /**
     * Returns the user's preferred technical format for virtual mic capture.
     * Guaranteed to return a valid format (defaults to 48kHz Stereo 16-bit).
     */
    fun getPreferredFormat(): android.media.AudioFormat

    /**
     * Sets the user's preferred technical format.
     */
    fun setPreferredFormat(sampleRate: Int, channelMask: Int)

    /**
     * Returns the mic presets that should be overridden by the virtual microphone.
     */
    fun getPreferredCapturePresets(): IntArray

    /**
     * Sets the mic presets to override.
     */
    fun setPreferredCapturePresets(presets: IntArray)

    /**
     * Returns whether file-based sample rate matching is enabled.
     */
    fun isMatchSampleRateEnabled(): Boolean

    /**
     * Sets whether file-based sample rate matching is enabled.
     */
    fun setMatchSampleRateEnabled(enabled: Boolean)

    /**
     * Returns whether file-based channel matching is enabled.
     */
    fun isMatchChannelsEnabled(): Boolean

    /**
     * Sets whether file-based channel matching is enabled.
     */
    fun setMatchChannelsEnabled(enabled: Boolean)

    /**
     * Returns whether the stream should start playing automatically when enabled.
     */
    fun isAutoStartEnabled(): Boolean

    /**
     * Sets whether auto-start is enabled.
     */
    fun setAutoStartEnabled(enabled: Boolean)

    /**
     * Returns the notification customization configuration.
     */
    fun getNotificationCustomization(): NotificationCustomizationConfig

    /**
     * Sets the notification customization configuration.
     */
    fun setNotificationCustomization(config: NotificationCustomizationConfig)
}
