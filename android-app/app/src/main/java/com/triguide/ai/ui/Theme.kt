package com.triguide.ai.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HighContrastDark = darkColorScheme(
    primary = Color(0xFFFFE14A),
    onPrimary = Color(0xFF111111),
    secondary = Color(0xFF9BE7FF),
    onSecondary = Color(0xFF042028),
    background = Color(0xFF0B0B0C),
    onBackground = Color(0xFFF7F7F7),
    surface = Color(0xFF1C1C1F),
    onSurface = Color(0xFFF7F7F7),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF2A0000),
)

@Composable
fun TriGuideTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HighContrastDark,
        content = content,
    )
}
