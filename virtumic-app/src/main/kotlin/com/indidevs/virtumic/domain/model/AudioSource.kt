package com.indidevs.virtumic.domain.model

import java.time.Duration

sealed interface AudioSource {
    val title: String
    val summary: String
    val duration: Duration?
    val isReady: Boolean

    data class File(val info: FileSourceInfo) : AudioSource {
        override val title: String = info.fileName
        override val summary: String = info.format
        override val duration: Duration = info.duration
        override val isReady: Boolean = true
    }

    data class App(val info: AppSourceInfo) : AudioSource {
        override val title: String = info.appName
        override val summary: String = info.description
        override val duration: Duration? = null
        override val isReady: Boolean = true
    }

    data class None(val sourceName: String = "source") : AudioSource {
        override val title: String = "No $sourceName selected"
        override val summary: String = "Select ${getArticle(sourceName)} $sourceName to continue"
        override val duration: Duration? = null
        override val isReady: Boolean = false

        private fun getArticle(name: String): String = if (name.firstOrNull()?.lowercaseChar() in
            setOf('a', 'e', 'i', 'o', 'u')
        ) {
            "an"
        } else {
            "a"
        }
    }
}
