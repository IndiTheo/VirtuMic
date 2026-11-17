package com.indidevs.virtumic.data.util

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

fun Bitmap.toIconBytes(): ByteArray? {
    return try {
        val targetSize = 256
        val scaledBitmap = if (width != targetSize || height != targetSize) {
            Bitmap.createScaledBitmap(this, targetSize, targetSize, true)
        } else {
            this
        }
        val stream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.toByteArray()
    } catch (_: Exception) {
        null
    }
}
