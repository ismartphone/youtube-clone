package com.youtubeclone.app.presentation.common

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Côté minimal d'une zone cliquable (ADR-030, exigence 3). */
val MinTouchTarget: Dp = 48.dp

/**
 * Impose une zone cliquable d'au moins 48 dp. À placer AVANT `clickable`, avec le visuel centré
 * (ou aligné) dans la zone : il garde sa taille, seule la cible grandit.
 */
fun Modifier.minTouchTarget(): Modifier = defaultMinSize(minWidth = MinTouchTarget, minHeight = MinTouchTarget)

/**
 * Dessine un fond en pastille de [visualHeight], centré dans la hauteur du composant. Sert aux
 * pastilles dont la zone cliquable fait 48 dp alors que le visuel garde sa taille (ADR-030).
 */
fun Modifier.pillBackground(color: Color, visualHeight: Dp): Modifier = drawBehind {
    val height = visualHeight.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(0f, (size.height - height) / 2f),
        size = Size(size.width, height),
        cornerRadius = CornerRadius(height / 2f),
    )
}
