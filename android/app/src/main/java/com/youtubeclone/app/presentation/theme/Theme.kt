package com.youtubeclone.app.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Thème toujours sombre, sans couleurs dynamiques, pour garder l'identité YouTube.
private val YoutubeColorScheme = darkColorScheme(
    primary = YoutubeRed,
    onPrimary = Color.White,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkBackground,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = DarkBackground,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = TextPrimary,
    error = YoutubeRed,
)

@Composable
fun YoutubeCloneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = YoutubeColorScheme,
        typography = Typography,
        content = content,
    )
}
