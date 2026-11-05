package com.youtubeclone.app.presentation.theme

import androidx.compose.ui.graphics.Color

// Palette sombre inspirée de YouTube.
val YoutubeRed = Color(0xFFFF0000)
val DarkBackground = Color(0xFF0F0F0F)
val DarkSurfaceVariant = Color(0xFF272727)
val TextPrimary = Color(0xFFF1F1F1)
val TextSecondary = Color(0xFFAAAAAA)

// Accessibilité (ADR-030, D1). Le rouge de marque reste YoutubeRed partout où il ne porte pas de
// texte ; ces deux variantes servent seulement quand la couleur porte du texte.
// Fond rouge sous du texte blanc : 4,53:1 (le rouge de marque donne 4,00:1).
val YoutubeRedFill = Color(0xFFEE0000)
// Texte rouge sur gris foncé (#272727) : 4,57:1 (le rouge de marque donne 3,74:1).
val YoutubeRedText = Color(0xFFFF4D4D)
