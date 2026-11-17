package com.indidevs.virtumic.ui.model

import com.indidevs.virtumic.data.util.toIconBytes
import com.indidevs.virtumic.domain.model.AppSourceInfo

fun AppItem.toAppSourceInfo(): AppSourceInfo {
    return AppSourceInfo(
        appName = label,
        description = packageName,
        packageName = packageName,
        iconBytes = icon.toIconBytes()
    )
}
