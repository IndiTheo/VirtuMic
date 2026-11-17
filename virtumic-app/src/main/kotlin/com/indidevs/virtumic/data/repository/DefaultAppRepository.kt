package com.indidevs.virtumic.data.repository

import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserManager
import androidx.core.graphics.drawable.toBitmap
import com.indidevs.virtumic.domain.repository.AppRepository
import com.indidevs.virtumic.ui.model.AppItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultAppRepository(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AppRepository {
    override suspend fun getInstalledApps(): List<AppItem> = withContext(ioDispatcher) {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager

        val userHandles = userManager.userProfiles

        userHandles.flatMap { userHandle ->
            launcherApps.getActivityList(null, userHandle).map { activity ->
                val appInfo = activity.applicationInfo
                val iconBitmap = activity.getIcon(0).toBitmap()
                AppItem(
                    label = activity.label.toString(),
                    packageName = appInfo.packageName,
                    icon = iconBitmap,
                    userHandle = userHandle
                )
            }
        }.distinctBy { "${it.packageName}_${it.userHandle}" }
            .sortedBy { it.label }
    }
}
