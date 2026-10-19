package com.triguide.ai.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Calm dark charcoal + warm sand accent.
 * High contrast for low vision; not neon yellow, not purple wash.
 */
private val CalmDark = darkColorScheme(
    primary = Color(0xFFE7D3A8),
    onPrimary = Color(0xFF1C140A),
    primaryContainer = Color(0xFF3D3424),
    onPrimaryContainer = Color(0xFFF4EFE6),
    secondary = Color(0xFFC9C0B4),
    onSecondary = Color(0xFF1C140A),
    secondaryContainer = Color(0xFF2C2822),
    onSecondaryContainer = Color(0xFFF4EFE6),
    tertiary = Color(0xFFC9C0B4),
    onTertiary = Color(0xFF1C140A),
    background = Color(0xFF141210),
    onBackground = Color(0xFFF4EFE6),
    surface = Color(0xFF242019),
    onSurface = Color(0xFFF4EFE6),
    surfaceVariant = Color(0xFF2C2822),
    onSurfaceVariant = Color(0xFFC9C0B4),
    surfaceTint = Color.Transparent,
    outline = Color(0xFF736B60),
    outlineVariant = Color(0xFF4A433A),
    error = Color(0xFFF0B4A8),
    onError = Color(0xFF3B1512),
    errorContainer = Color(0xFF3A2220),
    onErrorContainer = Color(0xFFF4EFE6),
)

@Composable
fun TriGuideTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CalmDark,
        content = content,
    )
}
