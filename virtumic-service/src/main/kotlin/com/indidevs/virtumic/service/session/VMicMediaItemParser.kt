package com.indidevs.virtumic.service.session

import android.content.Context
import android.os.Build
import androidx.media3.common.MediaItem
import com.indidevs.virtumic.service.model.AudioConfig

/**
 * Utility for parsing VirtuMic specific data from Media3 MediaItems.
 */
object VMicMediaItemParser {

    /**
     * Resolves the recorded application UID from a media item.
     * Supports appaudio:// URIs with a 'pkg' query parameter.
     */
    fun resolveCapturedAppUid(context: Context, mediaItem: MediaItem): Int {
        val uri = mediaItem.localConfiguration?.uri ?: return -1
        return if (uri.scheme == VMicServiceContract.URI_SCHEME_APPAUDIO) {
            val pkg = uri.getQueryParameter(VMicServiceContract.URI_PARAM_PACKAGE)
            try {
                pkg?.let {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.packageManager.getPackageUid(
                            it,
                            android.content.pm.PackageManager.PackageInfoFlags.of(0)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        context.packageManager.getPackageUid(it, 0)
                    }
                } ?: -1
            } catch (e: Exception) {
                -1
            }
        } else {
            -1
        }
    }

    /**
     * Resolves the [AudioConfig] from a media item's metadata extras.
     */
    fun resolveAudioConfig(mediaItem: MediaItem): AudioConfig? {
        val extras = mediaItem.mediaMetadata.extras ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(VMicServiceContract.EXTRA_AUDIO_CONFIG, AudioConfig::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(VMicServiceContract.EXTRA_AUDIO_CONFIG) as? AudioConfig
        }
    }
}
