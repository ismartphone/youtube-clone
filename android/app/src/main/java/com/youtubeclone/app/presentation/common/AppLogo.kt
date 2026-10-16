package com.youtubeclone.app.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.youtubeclone.app.R

/**
 * Logo dessiné : rectangle rouge arrondi avec le triangle de lecture blanc.
 * Ses proportions (80 x 56, coins de 16, triangle de 36) suivent la hauteur demandée.
 */
@Composable
fun AppLogo(modifier: Modifier = Modifier, height: Dp = 56.dp) {
    Box(
        modifier = modifier
            .size(width = height * 80 / 56, height = height)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(height * 16 / 56)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_play),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(height * 36 / 56),
        )
    }
}
