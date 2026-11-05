package com.youtubeclone.app.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
    // Texte d'erreur (ADR-030, D1) : la variante lisible sur gris, le rouge de marque ne porte pas de texte ici.
    error = YoutubeRedText,
)

@Composable
fun YoutubeCloneTheme(content: @Composable () -> Unit) {
    // Le réglage « réduire les animations » est lu une fois ici et partagé par tous les écrans.
    CompositionLocalProvider(LocalReduceMotion provides rememberReduceMotion()) {
        MaterialTheme(
            colorScheme = YoutubeColorScheme,
            typography = Typography,
            content = content,
        )
    }
}
