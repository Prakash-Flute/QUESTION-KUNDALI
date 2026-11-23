package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val BubbleLogColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = InkDark,
    primaryContainer = NeonCyanContainer,
    onPrimaryContainer = NeonCyan,
    secondary = NeonViolet,
    onSecondary = TextPrimary,
    secondaryContainer = NeonVioletContainer,
    onSecondaryContainer = NeonVioletSoft,
    tertiary = NeonVioletSoft,
    onTertiary = InkDark,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = TextSecondary,
    outline = ObsidianStroke,
    outlineVariant = ObsidianStroke,
    error = NeonCoral,
    onError = InkDark,
    errorContainer = NeonCoralContainer,
    onErrorContainer = NeonCoral,
)

@Composable
fun BubbleLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BubbleLogColorScheme,
        typography = BubbleLogTypography,
        content = content,
    )
}
