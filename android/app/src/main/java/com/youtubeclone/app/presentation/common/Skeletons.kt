package com.youtubeclone.app.presentation.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.youtubeclone.app.R
import com.youtubeclone.app.presentation.theme.LocalReduceMotion

// Squelettes de chargement : la forme du contenu à venir, en gris, qui pulse doucement.
// Avec les animations réduites, ils restent fixes.

private const val PULSE_MS = 800
private const val PULSE_MIN_ALPHA = 0.4f

/** Opacité partagée par tous les blocs d'un même squelette, pour qu'ils pulsent ensemble. */
@Composable
private fun pulseAlpha(): Float {
    if (LocalReduceMotion.current) return 1f
    val transition = rememberInfiniteTransition(label = "skeletonPulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = PULSE_MIN_ALPHA,
        animationSpec = infiniteRepeatable(tween(PULSE_MS), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    return alpha
}

@Composable
private fun SkeletonBlock(modifier: Modifier, shape: Shape = RoundedCornerShape(4.dp)) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant, shape))
}

/** Squelette d'une carte vidéo : miniature 16:9, titre et ligne d'informations. */
@Composable
private fun VideoCardSkeleton() {
    Column(modifier = Modifier.fillMaxWidth()) {
        SkeletonBlock(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            shape = RoundedCornerShape(0.dp),
        )
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SkeletonBlock(Modifier.fillMaxWidth(0.85f).height(16.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.5f).height(12.dp))
        }
    }
}

/** Chargement du fil d'accueil. */
@Composable
fun VideoListSkeleton(modifier: Modifier = Modifier, count: Int = 3) {
    val alpha = pulseAlpha()
    val description = stringResource(R.string.loading)
    Column(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(count) { VideoCardSkeleton() }
    }
}

/** Chargement de la page de chaîne : en-tête (avatar, nom, bouton) puis vidéos. */
@Composable
fun ChannelPageSkeleton(modifier: Modifier = Modifier) {
    val alpha = pulseAlpha()
    val description = stringResource(R.string.loading)
    Column(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SkeletonBlock(Modifier.size(72.dp), shape = CircleShape)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                    SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(20.dp))
                    SkeletonBlock(Modifier.fillMaxWidth(0.4f).height(12.dp))
                }
            }
            SkeletonBlock(Modifier.fillMaxWidth().height(40.dp), shape = RoundedCornerShape(20.dp))
        }
        repeat(2) { VideoCardSkeleton() }
    }
}
