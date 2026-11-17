package com.indidevs.virtumic.domain.repository

import android.net.Uri
import com.indidevs.virtumic.domain.model.FileSourceInfo

interface AudioMetadataRepository {
    suspend fun getFileSourceInfo(uri: Uri): FileSourceInfo?
}
