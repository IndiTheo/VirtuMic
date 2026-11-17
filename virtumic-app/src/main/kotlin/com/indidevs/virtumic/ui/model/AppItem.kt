package com.indidevs.virtumic.ui.model

import android.graphics.Bitmap
import android.os.UserHandle

data class AppItem(
    val label: String,
    val packageName: String,
    val icon: Bitmap,
    val userHandle: UserHandle
)
