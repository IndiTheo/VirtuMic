package com.indidevs.virtumic.domain.repository

import com.indidevs.virtumic.ui.model.AppItem

interface AppRepository {
    suspend fun getInstalledApps(): List<AppItem>
}
