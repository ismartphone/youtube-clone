package com.youtubeclone.app.presentation.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.youtubeclone.app.presentation.theme.LocalReduceMotion
import com.youtubeclone.app.presentation.theme.Motion
import kotlinx.coroutines.delay

// Apparition des cartes à la fin d'un chargement : fondu avec une légère montée, décalé carte
// par carte. Seules les premières cartes sont animées, une seule fois : celles qu'on découvre
// en faisant défiler la liste s'affichent normalement.

private const val STAGGER_MS = 50L
private const val MAX_ANIMATED_ITEMS = 6
private val RISE = 16.dp

/** Mémorise les éléments déjà apparus. Une nouvelle instance (nouveau chargement) les rejoue. */
class StaggeredAppearance internal constructor() {
    internal val shown = HashSet<Any>()
}

@Composable
fun rememberStaggeredAppearance(key: Any?): StaggeredAppearance = remember(key) { StaggeredAppearance() }

@Composable
fun Modifier.staggeredAppearance(state: StaggeredAppearance, itemKey: Any, index: Int): Modifier {
    val reduceMotion = LocalReduceMotion.current
    val animate = remember(state, itemKey) { index < MAX_ANIMATED_ITEMS && itemKey !in state.shown }
    val progress = remember(state, itemKey) { Animatable(if (animate) 0f else 1f) }
    val risePx = with(LocalDensity.current) { RISE.toPx() }

    LaunchedEffect(state, itemKey) {
        state.shown += itemKey
        if (!animate) return@LaunchedEffect
        if (reduceMotion) {
            progress.animateTo(1f, tween(Motion.REDUCED_FADE_MS))
        } else {
            delay(index * STAGGER_MS)
            progress.animateTo(1f, tween(Motion.FADE_MS, easing = Motion.Easing))
        }
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = if (reduceMotion) 0f else (1f - progress.value) * risePx
    }
}
