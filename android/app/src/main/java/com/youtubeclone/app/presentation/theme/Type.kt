package com.youtubeclone.app.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    // Titre des cartes vidéo.
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    // Ligne d'informations sous le titre (chaîne, vues).
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    // Badge de durée sur les miniatures.
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)
