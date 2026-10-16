package com.youtubeclone.app.presentation.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.youtubeclone.app.R
import com.youtubeclone.app.presentation.common.AppLogo
import com.youtubeclone.app.presentation.theme.LocalReduceMotion
import com.youtubeclone.app.presentation.theme.Motion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// Chronologie, environ 2 secondes au total.
private const val APPEAR_MS = 600 // 1-2. le logo grossit de 0,6 à 1 avec un léger rebond, en fondu
private const val PAUSE_MS = 400L // 3. courte pause
private const val COMPOSE_MS = 500 // 4. le logo glisse à gauche, « Clone » apparaît à sa droite
private const val HOLD_MS = 200L //    le mot-symbole complet reste un instant
private const val FADE_OUT_MS = 300 // 5. fondu de sortie

// Si l'application n'est toujours pas prête, on n'attend pas plus : l'écran suivant affiche
// alors son propre état de chargement (réseau très lent, par exemple).
private const val MAX_EXTRA_WAIT_MS = 3_000L

private val WORDMARK_GAP = 12.dp

/**
 * Animation d'ouverture, inspirée de celle de YouTube. Elle recouvre l'écran suivant, déjà composé
 * dessous pour qu'il charge en même temps : l'animation ne retarde pas l'application.
 * Elle ne s'efface qu'une fois [isAppReady] vrai, puis appelle [onFinished].
 */
@Composable
fun OpeningAnimation(
    isAppReady: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val logoScale = remember { Animatable(0.6f) }
    val logoAlpha = remember { Animatable(0f) }
    // 0 : logo seul au centre ; 1 : logo à gauche et mot « Clone » visible.
    val wordmarkProgress = remember { Animatable(0f) }
    val overlayAlpha = remember { Animatable(1f) }

    val reduceMotion = LocalReduceMotion.current
    val currentIsAppReady by rememberUpdatedState(isAppReady)
    val currentOnFinished by rememberUpdatedState(onFinished)
    var wordWidthPx by remember { mutableIntStateOf(0) }
    val gapPx = with(LocalDensity.current) { WORDMARK_GAP.toPx() }

    LaunchedEffect(Unit) {
        if (reduceMotion) {
            // Animations réduites : le mot-symbole complet apparaît en simple fondu, sans mouvement.
            logoScale.snapTo(1f)
            wordmarkProgress.snapTo(1f)
            logoAlpha.animateTo(1f, tween(Motion.REDUCED_FADE_MS))
        } else {
            coroutineScope {
                launch {
                    logoScale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
                    )
                }
                launch { logoAlpha.animateTo(1f, tween(APPEAR_MS)) }
            }
            delay(PAUSE_MS)
            wordmarkProgress.animateTo(1f, tween(COMPOSE_MS, easing = FastOutSlowInEasing))
        }
        delay(HOLD_MS)
        // Préférences lues et premier écran chargé : si c'est plus long que l'animation, on attend.
        withTimeoutOrNull(MAX_EXTRA_WAIT_MS) {
            snapshotFlow { currentIsAppReady }.first { it }
        }
        overlayAlpha.animateTo(0f, tween(if (reduceMotion) Motion.REDUCED_FADE_MS else FADE_OUT_MS))
        currentOnFinished()
    }

    val description = stringResource(R.string.app_name)
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha.value }
            .background(MaterialTheme.colorScheme.background)
            // L'écran suivant, dessous, ne doit recevoir ni toucher ni lecture d'accessibilité.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.graphicsLayer {
                // Au départ, seul le logo est centré : la rangée est décalée vers la droite de la
                // moitié du mot et de l'espace, puis revient à sa place pendant que le mot apparaît.
                translationX = (1f - wordmarkProgress.value) * (wordWidthPx + gapPx) / 2f
            },
        ) {
            AppLogo(
                modifier = Modifier.graphicsLayer {
                    scaleX = logoScale.value
                    scaleY = logoScale.value
                    alpha = logoAlpha.value
                },
            )
            Spacer(Modifier.width(WORDMARK_GAP))
            Text(
                text = stringResource(R.string.opening_wordmark),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp,
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .onSizeChanged { wordWidthPx = it.width }
                    .graphicsLayer { alpha = wordmarkProgress.value },
            )
        }
    }
}
