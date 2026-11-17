package com.indidevs.virtumic.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val Primary80 = Color(0xFFD0BCFF)
val Secondary80 = Color(0xFFCCC2DC)
val Tertiary80 = Color(0xFFEFB8C8)

val Primary40 = Color(0xFF6650a4)
val Secondary40 = Color(0xFF625b71)
val Tertiary40 = Color(0xFF7D5260)

@Immutable
data class SemanticColors(
    val success: Color = Color(0xFF4CAF50),
    val error: Color = Color(0xFFF44336),
    val warning: Color = Color(0xFFFF9800)
)

val LocalSemanticColors = staticCompositionLocalOf { SemanticColors() }

@Suppress("UnusedReceiverParameter")
val MaterialTheme.semanticColors: SemanticColors
    @Composable
    @ReadOnlyComposable
    get() = LocalSemanticColors.current
