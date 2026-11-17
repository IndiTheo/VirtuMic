package com.indidevs.virtumic.domain.model

data class AppSourceInfo(
    val appName: String,
    val description: String,
    val packageName: String? = null,
    val iconBytes: ByteArray? = null
)
