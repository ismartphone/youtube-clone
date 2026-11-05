package com.youtubeclone.app.presentation.live

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.youtubeclone.app.R
import com.youtubeclone.app.presentation.common.MinTouchTarget
import com.youtubeclone.app.domain.model.LIVE_REACTIONS
import com.youtubeclone.app.domain.model.LIVE_REACTIONS_MAX_ON_SCREEN
import com.youtubeclone.app.presentation.theme.LocalReduceMotion
import com.youtubeclone.app.presentation.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlin.math.sin

// Réactions d'un live (ADR-029, D6) : une barre de six émojis pour réagir, et des émojis qui
// remontent à l'écran, reçus de la salle (les siens comme ceux des autres).

private const val RISE_MS = 2400
private const val REDUCED_VISIBLE_MS = 700
private const val LANES = 5

/** Barre des six réactions, réservée aux comptes connectés. */
@Composable
fun ReactionBar(onReact: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val description = stringResource(R.string.live_react)
        LIVE_REACTIONS.forEach { emoji ->
            Box(
                modifier = Modifier
                    .size(MinTouchTarget)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = description) { onReact(emoji) },
                contentAlignment = Alignment.Center,
            ) { Text(text = emoji, fontSize = 24.sp) }
        }
    }
}

private class FloatingEmoji(val id: Long, val emoji: String, val lane: Int)

/**
 * Surcouche des émojis qui montent depuis le bas à droite de la zone. Au plus
 * [LIVE_REACTIONS_MAX_ON_SCREEN] à la fois : au-delà, le plus ancien disparaît. Avec « Supprimer les
 * animations », l'émoji ne monte pas : il apparaît puis s'efface sur place.
 */
@Composable
fun FloatingReactions(reactions: Flow<String>, modifier: Modifier = Modifier) {
    val active: SnapshotStateList<FloatingEmoji> = remember { mutableStateListOf() }
    var nextId by remember { mutableLongStateOf(0L) }
    LaunchedEffect(reactions) {
        reactions.collect { emoji ->
            val id = nextId++
            active.add(FloatingEmoji(id, emoji, lane = (id % LANES).toInt()))
            while (active.size > LIVE_REACTIONS_MAX_ON_SCREEN) active.removeAt(0)
        }
    }
    Box(modifier = modifier) {
        for (item in active) {
            key(item.id) { RisingEmoji(item, onDone = { active.remove(item) }) }
        }
    }
}

@Composable
private fun BoxScope.RisingEmoji(item: FloatingEmoji, onDone: () -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    val progress = remember { Animatable(0f) }
    val density = LocalDensity.current
    LaunchedEffect(item.id) {
        if (reduceMotion) {
            progress.animateTo(1f, tween(Motion.REDUCED_FADE_MS))
            delay(REDUCED_VISIBLE_MS.toLong())
        } else {
            progress.animateTo(1f, tween(RISE_MS, easing = LinearEasing))
        }
        onDone()
    }
    val laneOffset = (item.lane * 14).dp
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 8.dp + laneOffset, bottom = 8.dp)
            .graphicsLayer {
                val p = progress.value
                if (reduceMotion) {
                    alpha = p
                } else {
                    // Monte de ~220 dp en ondulant, apparaît vite, s'efface sur le dernier tiers.
                    translationY = -p * with(density) { 220.dp.toPx() }
                    translationX = sin(p * 6f + item.lane) * with(density) { 10.dp.toPx() }
                    alpha = if (p < 0.1f) p * 10f else if (p > 0.66f) (1f - p) * 3f else 1f
                }
            },
    ) { Text(text = item.emoji, fontSize = 28.sp) }
}
