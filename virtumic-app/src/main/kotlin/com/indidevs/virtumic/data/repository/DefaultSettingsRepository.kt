package com.indidevs.virtumic.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import com.indidevs.android.privilegehub.PrivilegeProvider
import com.indidevs.virtumic.data.util.toIconBytes
import com.indidevs.virtumic.domain.model.AppSourceInfo
import com.indidevs.virtumic.domain.model.SourceType
import com.indidevs.virtumic.domain.repository.NotifField
import com.indidevs.virtumic.domain.repository.NotifFieldConfig
import com.indidevs.virtumic.domain.repository.NotifFieldRules
import com.indidevs.virtumic.domain.repository.NotifLineConfig
import com.indidevs.virtumic.domain.repository.NotifSeparator
import com.indidevs.virtumic.domain.repository.NotificationCustomizationConfig
import com.indidevs.virtumic.domain.repository.SettingsRepository

class DefaultSettingsRepository(private val context: Context) : SettingsRepository {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "virtumic_settings"

        private const val KEY_SOURCE_TYPE = "selected_source_type"
        private const val KEY_FILE_URI = "selected_file_uri"
        private const val KEY_APP_NAME = "selected_app_name"
        private const val KEY_APP_PACKAGE = "selected_app_package"
        private const val KEY_PRIVILEGE_MODE = "privilege_mode"

        // Technical Audio Keys
        private const val KEY_SAMPLE_RATE = "audio_sample_rate"
        private const val KEY_CHANNEL_MASK = "audio_channel_mask"
        private const val KEY_MATCH_SAMPLE_RATE = "audio_match_sample_rate"
        private const val KEY_MATCH_CHANNELS = "audio_match_channels"
        private const val KEY_CAPTURE_PRESETS = "audio_capture_presets"
        private const val KEY_AUTO_START = "auto_start_streaming"

        // Notification Customization Keys
        private const val KEY_FILE_TITLE_PRIMARY = "notif_file_title_primary"
        private const val KEY_FILE_TITLE_APPEND = "notif_file_title_append"
        private const val KEY_FILE_TITLE_SEP = "notif_file_title_sep"

        private const val KEY_FILE_ARTIST_PRIMARY = "notif_file_artist_primary"
        private const val KEY_FILE_ARTIST_APPEND = "notif_file_artist_append"
        private const val KEY_FILE_ARTIST_SEP = "notif_file_artist_sep"

        private const val KEY_APP_TITLE_PRIMARY = "notif_app_title_primary"
        private const val KEY_APP_TITLE_APPEND = "notif_app_title_append"
        private const val KEY_APP_TITLE_SEP = "notif_app_title_sep"

        private const val KEY_APP_ARTIST_PRIMARY = "notif_app_artist_primary"
        private const val KEY_APP_ARTIST_APPEND = "notif_app_artist_append"
        private const val KEY_APP_ARTIST_SEP = "notif_app_artist_sep"
    }

    override fun getSelectedSourceType(): SourceType {
        val name = prefs.getString(KEY_SOURCE_TYPE, SourceType.FILE.name)
        return try {
            SourceType.valueOf(name!!)
        } catch (_: Exception) {
            SourceType.FILE
        }
    }

    override fun setSelectedSourceType(type: SourceType) {
        prefs.edit().putString(KEY_SOURCE_TYPE, type.name).apply()
    }

    override fun getSelectedFileUri(): Uri? {
        val uriString = prefs.getString(KEY_FILE_URI, null)
        return uriString?.toUri()
    }

    override fun setSelectedFileUri(uri: Uri?) {
        prefs.edit { putString(KEY_FILE_URI, uri?.toString()) }
    }

    override fun getSelectedAppInfo(): AppSourceInfo? {
        val name = prefs.getString(KEY_APP_NAME, null)
        val packageName = prefs.getString(KEY_APP_PACKAGE, null)
        return if (name != null && packageName != null) {
            AppSourceInfo(
                appName = name,
                description = packageName,
                packageName = packageName,
                iconBytes = loadAppIconBytes(packageName)
            )
        } else {
            null
        }
    }

    private fun loadAppIconBytes(packageName: String?): ByteArray? {
        if (packageName.isNullOrBlank()) return null
        return try {
            context.packageManager.getApplicationIcon(packageName).toBitmap().toIconBytes()
        } catch (_: Exception) {
            null
        }
    }

    override fun setSelectedAppInfo(info: AppSourceInfo?) {
        prefs.edit().apply {
            putString(KEY_APP_NAME, info?.appName)
            putString(KEY_APP_PACKAGE, info?.packageName ?: info?.description)
        }.apply()
    }

    override fun getPrivilegeMode(): String {
        return prefs.getString(
            KEY_PRIVILEGE_MODE,
            PrivilegeProvider.ID_NONE
        ) ?: PrivilegeProvider.ID_NONE
    }

    override fun setPrivilegeMode(mode: String) {
        prefs.edit { putString(KEY_PRIVILEGE_MODE, mode) }
    }

    override fun getPreferredFormat(): android.media.AudioFormat {
        val rate = prefs.getInt(KEY_SAMPLE_RATE, SettingsRepository.DEFAULT_SAMPLE_RATE)
        val mask = prefs.getInt(KEY_CHANNEL_MASK, SettingsRepository.DEFAULT_CHANNEL_MASK)

        return android.media.AudioFormat.Builder()
            .setSampleRate(rate)
            .setChannelMask(mask)
            .setEncoding(SettingsRepository.DEFAULT_ENCODING)
            .build()
    }

    override fun setPreferredFormat(sampleRate: Int, channelMask: Int) {
        prefs.edit {
            putInt(KEY_SAMPLE_RATE, sampleRate)
            putInt(KEY_CHANNEL_MASK, channelMask)
        }
    }

    override fun getPreferredCapturePresets(): IntArray {
        val presetsString = prefs.getString(KEY_CAPTURE_PRESETS, null)
        if (presetsString == null) {
            return intArrayOf(
                android.media.MediaRecorder.AudioSource.DEFAULT,
                android.media.MediaRecorder.AudioSource.MIC,
                android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION
            )
        }
        return presetsString.split(",").mapNotNull { it.toIntOrNull() }.toIntArray()
    }

    override fun setPreferredCapturePresets(presets: IntArray) {
        prefs.edit {
            putString(KEY_CAPTURE_PRESETS, presets.joinToString(","))
        }
    }

    override fun isMatchSampleRateEnabled(): Boolean {
        return prefs.getBoolean(KEY_MATCH_SAMPLE_RATE, true)
    }

    override fun setMatchSampleRateEnabled(enabled: Boolean) {
        prefs.edit {
            putBoolean(KEY_MATCH_SAMPLE_RATE, enabled)
        }
    }

    override fun isMatchChannelsEnabled(): Boolean {
        return prefs.getBoolean(KEY_MATCH_CHANNELS, true)
    }

    override fun setMatchChannelsEnabled(enabled: Boolean) {
        prefs.edit {
            putBoolean(KEY_MATCH_CHANNELS, enabled)
        }
    }

    override fun isAutoStartEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_START, true)
    }

    override fun setAutoStartEnabled(enabled: Boolean) {
        prefs.edit {
            putBoolean(KEY_AUTO_START, enabled)
        }
    }

    override fun getNotificationCustomization(): NotificationCustomizationConfig {
        return NotificationCustomizationConfig(
            fileTitle = parseLineConfig(
                KEY_FILE_TITLE_PRIMARY,
                KEY_FILE_TITLE_APPEND,
                KEY_FILE_TITLE_SEP,
                NotifField.ID3_TITLE
            ),
            fileArtist = parseLineConfig(
                KEY_FILE_ARTIST_PRIMARY,
                KEY_FILE_ARTIST_APPEND,
                KEY_FILE_ARTIST_SEP,
                NotifField.ID3_ARTIST
            ),
            appTitle = parseLineConfig(
                KEY_APP_TITLE_PRIMARY,
                KEY_APP_TITLE_APPEND,
                KEY_APP_TITLE_SEP,
                NotifField.APP_NAME
            ),
            appArtist = parseLineConfig(
                KEY_APP_ARTIST_PRIMARY,
                KEY_APP_ARTIST_APPEND,
                KEY_APP_ARTIST_SEP,
                NotifField.FULL_AUDIO_FORMAT
            )
        )
    }

    override fun setNotificationCustomization(config: NotificationCustomizationConfig) {
        prefs.edit {
            putLineConfig(
                KEY_FILE_TITLE_PRIMARY,
                KEY_FILE_TITLE_APPEND,
                KEY_FILE_TITLE_SEP,
                config.fileTitle
            )
            putLineConfig(
                KEY_FILE_ARTIST_PRIMARY,
                KEY_FILE_ARTIST_APPEND,
                KEY_FILE_ARTIST_SEP,
                config.fileArtist
            )
            putLineConfig(
                KEY_APP_TITLE_PRIMARY,
                KEY_APP_TITLE_APPEND,
                KEY_APP_TITLE_SEP,
                config.appTitle
            )
            putLineConfig(
                KEY_APP_ARTIST_PRIMARY,
                KEY_APP_ARTIST_APPEND,
                KEY_APP_ARTIST_SEP,
                config.appArtist
            )
        }
    }

    private fun parseLineConfig(
        primaryKey: String,
        appendKey: String,
        sepKey: String,
        defaultPrimary: NotifField,
        defaultAppend: NotifField = NotifField.NONE
    ): NotifLineConfig {
        return NotifLineConfig(
            primaryConfig = parseFieldConfig(primaryKey, defaultPrimary),
            appendableConfig = parseFieldConfig(appendKey, defaultAppend),
            separator = parseSeparator(prefs.getString(sepKey, null), NotifSeparator.DOT)
        )
    }

    private fun android.content.SharedPreferences.Editor.putLineConfig(
        primaryKey: String,
        appendKey: String,
        sepKey: String,
        lineConfig: NotifLineConfig
    ) {
        putFieldConfig(primaryKey, lineConfig.primaryConfig)
        putFieldConfig(appendKey, lineConfig.appendableConfig)
        putString(sepKey, lineConfig.separator.name)
    }

    private fun parseFieldConfig(fieldKey: String, defaultField: NotifField): NotifFieldConfig {
        val useFbKey = fieldKey + "_use_fb"
        val fbFieldKey = fieldKey + "_fb_field"
        val field = parseField(prefs.getString(fieldKey, null), defaultField)
        val defaultFallback = NotifFieldRules.getDefaultFallback(field)
        val defaultUseFb = (defaultFallback != null)
        val useFallback = prefs.getBoolean(useFbKey, defaultUseFb)
        val fallbackField = parseField(
            prefs.getString(fbFieldKey, null),
            defaultFallback ?: NotifField.NONE
        )
        return NotifFieldConfig(
            field = field,
            useFallback = useFallback,
            fallbackField = fallbackField
        )
    }

    private fun android.content.SharedPreferences.Editor.putFieldConfig(
        fieldKey: String,
        config: NotifFieldConfig
    ) {
        val useFbKey = fieldKey + "_use_fb"
        val fbFieldKey = fieldKey + "_fb_field"
        putString(fieldKey, config.field.name)
        putBoolean(useFbKey, config.useFallback)
        putString(fbFieldKey, config.fallbackField.name)
    }

    private fun parseField(value: String?, defaultVal: NotifField): NotifField {
        if (value == null) return defaultVal
        return try {
            NotifField.valueOf(value)
        } catch (_: Exception) {
            defaultVal
        }
    }

    private fun parseSeparator(value: String?, defaultVal: NotifSeparator): NotifSeparator {
        if (value == null) return defaultVal
        return try {
            NotifSeparator.valueOf(value)
        } catch (_: Exception) {
            defaultVal
        }
    }
}
