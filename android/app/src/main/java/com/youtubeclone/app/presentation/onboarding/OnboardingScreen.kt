package com.youtubeclone.app.presentation.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.analytics.PaywallSource
import com.youtubeclone.app.domain.model.Video
import com.youtubeclone.app.domain.model.VideoFormat
import com.youtubeclone.app.presentation.auth.LoginScreen
import com.youtubeclone.app.presentation.auth.RegisterScreen
import com.youtubeclone.app.presentation.common.AppLogo
import com.youtubeclone.app.presentation.common.VideoThumbnailImage
import com.youtubeclone.app.presentation.common.asText
import com.youtubeclone.app.presentation.premium.PaywallScreen
import com.youtubeclone.app.presentation.premium.PaywallViewModel
import com.youtubeclone.app.presentation.theme.LocalReduceMotion
import com.youtubeclone.app.presentation.theme.Motion
import kotlinx.coroutines.delay

// Parcours d'accueil (ADR-028). UN composant affiche toutes les étapes de ONBOARDING_STEPS : barre de
// progression, transition glissée, apparition en cascade, « Suivant » et « Passer ». Il choisit le
// contenu central selon le type de l'étape ; les règles (enregistrement, mesures, sons) sont dans
// OnboardingViewModel.

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    BackHandler(enabled = state.index > 0) { viewModel.back() }
    // Application passée en arrière-plan avant la fin : parcours compté comme abandonné (ADR-028, D4).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onBackgrounded() }

    // Étape « notifications » : « Suivant » demande la permission système, puis passe à l'étape
    // suivante quelle que soit la réponse. Sous Android 12 et moins, la permission n'existe pas.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.next()
    }
    val onNext = {
        val kind = state.step.kind
        val needsPermission = kind is StepKind.Action && kind.action == StepAction.REQUEST_NOTIFICATIONS &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.next()
    }

    val step = state.step
    val required = remember(state.categories) { requiredInterests(state) }
    val canGoNext = when (val kind = step.kind) {
        is StepKind.MultiChoice -> state.selectedInterests.size >= required.coerceAtMost(kind.minChoices)
        is StepKind.SingleChoice -> state.preferredFormat != null
        else -> true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        ProgressBar(index = state.index, count = ONBOARDING_STEPS.size)
        val reduceMotion = LocalReduceMotion.current
        AnimatedContent(
            targetState = state.index,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                if (reduceMotion) {
                    fadeIn(tween(Motion.REDUCED_FADE_MS)) togetherWith fadeOut(tween(Motion.REDUCED_FADE_MS))
                } else {
                    // Glissement dans le sens du parcours : vers la gauche en avançant, vers la droite en reculant.
                    // Courbe standard (accélération puis décélération), sans rebond.
                    val direction = if (state.forward) 1 else -1
                    (slideInHorizontally(tween(STEP_MS, easing = Motion.Easing)) { it * direction / 3 } +
                        fadeIn(tween(STEP_MS, easing = Motion.Easing))) togetherWith
                        (slideOutHorizontally(tween(STEP_MS, easing = Motion.Easing)) { -it * direction / 3 } +
                            fadeOut(tween(STEP_MS / 2, easing = Motion.Easing)))
                }
            },
            label = "onboarding-step",
        ) { index ->
            StepContent(
                step = ONBOARDING_STEPS[index],
                state = state,
                required = required,
                viewModel = viewModel,
            )
        }
        StepFooter(step = step, canGoNext = canGoNext, onNext = onNext, onSkip = viewModel::skip)
    }
}

// Catégories à cocher : 3, ou toutes si l'API en renvoie moins (ADR-028).
private fun requiredInterests(state: OnboardingUiState): Int {
    val min = (ONBOARDING_STEPS.firstNotNullOfOrNull { it.kind as? StepKind.MultiChoice })?.minChoices ?: 0
    val loaded = state.categories as? OnboardingCategories.Loaded ?: return min
    return loaded.categories.size.coerceAtMost(min)
}

@Composable
private fun ProgressBar(index: Int, count: Int) {
    // La jauge glisse d'une position à l'autre ; animations réduites : elle change sans mouvement.
    val reduceMotion = LocalReduceMotion.current
    val progress by animateFloatAsState(
        targetValue = (index + 1).toFloat() / count,
        animationSpec = if (reduceMotion) snap() else tween(STEP_MS, easing = Motion.Easing),
        label = "onboarding-progress",
    )
    val description = stringResource(R.string.onboarding_progress, index + 1, count)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .height(4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { contentDescription = description },
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
    }
}

@Composable
private fun StepFooter(step: OnboardingStep, canGoNext: Boolean, onNext: () -> Unit, onSkip: () -> Unit) {
    if (!step.showNext && step.skipLabel == null) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (step.showNext) {
            val nextInteraction = remember { MutableInteractionSource() }
            Button(
                onClick = onNext,
                enabled = canGoNext,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.background,
                ),
                interactionSource = nextInteraction,
                modifier = Modifier
                    .pressScale(nextInteraction)
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(stringResource(R.string.onboarding_next), fontWeight = FontWeight.Bold)
            }
        }
        // « Passer » discret : un simple texte gris, sous le bouton principal.
        step.skipLabel?.let { label ->
            val skipInteraction = remember { MutableInteractionSource() }
            TextButton(
                onClick = onSkip,
                interactionSource = skipInteraction,
                modifier = Modifier.pressScale(skipInteraction),
            ) {
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Retour visuel à l'appui : le bouton se réduit légèrement tant qu'il est pressé. Rien quand
 * l'utilisateur a demandé moins d'animations (l'effet d'onde du bouton reste).
 */
@Composable
private fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier {
    if (LocalReduceMotion.current) return this
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESSED_SCALE else 1f,
        animationSpec = tween(PRESS_MS, easing = Motion.Easing),
        label = "onboarding-press",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

private const val PRESSED_SCALE = 0.96f
private const val PRESS_MS = 100

/** Contenu central d'une étape : choisi selon son type, jamais selon son identifiant. */
@Composable
private fun StepContent(step: OnboardingStep, state: OnboardingUiState, required: Int, viewModel: OnboardingViewModel) {
    when (val kind = step.kind) {
        is StepKind.Action -> when (kind.action) {
            StepAction.REGISTER -> AccountStep(visit = state.visit, viewModel = viewModel)
            StepAction.PAYWALL -> PaywallStep(visit = state.visit, viewModel = viewModel)
            StepAction.REQUEST_NOTIFICATIONS -> Page(step, required, visual = { Visual(kind.visual, state) })
        }
        is StepKind.Presentation -> Page(step, required, visual = { Visual(kind.visual, state) })
        is StepKind.MultiChoice -> Page(step, required) { InterestChoices(state, required, viewModel) }
        is StepKind.SingleChoice -> Page(step, required) { SingleChoice(kind, state, viewModel::selectFormat) }
        is StepKind.Loading -> LoadingStep(step, kind, onDone = { viewModel.advanceAutomatically(step.id) })
        is StepKind.Recap -> Page(step, required) { Recap(kind, state) }
    }
}

// Mise en page commune : visuel (ou rien), titre, texte, puis le contenu de l'étape. Les éléments
// apparaissent l'un après l'autre : titre, texte, puis visuel ou options.
@Composable
private fun Page(
    step: OnboardingStep,
    required: Int,
    visual: (@Composable () -> Unit)? = null,
    body: (@Composable () -> Unit)? = null,
) {
    val appName = stringResource(R.string.app_name)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (visual != null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .cascadeIn(3),
                contentAlignment = Alignment.Center,
            ) { visual() }
        }
        Column(
            modifier = Modifier
                .then(if (visual == null) Modifier.weight(1f) else Modifier)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(step.title, appName),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.cascadeIn(1),
            )
            step.text?.let { text ->
                Text(
                    text = stringResource(text, required),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.cascadeIn(2),
                )
            }
            if (body != null) Box(Modifier.fillMaxWidth().padding(top = 8.dp).cascadeIn(3)) { body() }
        }
    }
}

/**
 * Apparition en cascade (ADR-028) : fondu avec une légère montée, élément après élément. [order]
 * donne le rang. Rejouée à chaque entrée dans l'étape, puisque son contenu est recréé ; réduite à un
 * fondu court quand l'utilisateur a demandé moins d'animations.
 */
@Composable
private fun Modifier.cascadeIn(order: Int): Modifier {
    val reduceMotion = LocalReduceMotion.current
    val progress = remember { Animatable(0f) }
    val risePx = with(LocalDensity.current) { 20.dp.toPx() }
    LaunchedEffect(Unit) {
        if (reduceMotion) {
            progress.animateTo(1f, tween(Motion.REDUCED_FADE_MS))
        } else {
            delay(CASCADE_MS * order)
            progress.animateTo(1f, tween(CASCADE_FADE_MS, easing = Motion.Easing))
        }
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = if (reduceMotion) 0f else (1f - progress.value) * risePx
    }
}

// Écart d'environ 50 ms entre deux éléments : senti, pas compté.
private const val CASCADE_MS = 50L
private const val CASCADE_FADE_MS = 250

/** Passage d'une étape à l'autre (glissement et fondu), et glissement de la jauge de progression. */
private const val STEP_MS = 300

// ---------------------------------------------------------------------------------------------
// Visuels

@Composable
private fun Visual(visual: StepVisual?, state: OnboardingUiState) {
    when (visual) {
        StepVisual.LOGO -> AnimatedLogo()
        StepVisual.VIDEO_MOSAIC -> VideoMosaic(state.longVideos)
        StepVisual.SHORTS_ROW -> ShortsRow(state.shorts)
        StepVisual.NOTIFICATIONS -> NotificationsVisual()
        null -> Unit
    }
}

// Logo animé : il grossit en rebondissant à l'arrivée, puis respire doucement.
@Composable
private fun AnimatedLogo() {
    val reduceMotion = LocalReduceMotion.current
    val entrance = remember { Animatable(if (reduceMotion) 1f else 0.4f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(
            1f,
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        )
    }
    val pulse by rememberInfiniteTransition(label = "logo-pulse").animateFloat(
        initialValue = 1f,
        targetValue = if (reduceMotion) 1f else 1.06f,
        animationSpec = infiniteRepeatable(tween(1_100, easing = Motion.Easing), RepeatMode.Reverse),
        label = "logo-pulse-value",
    )
    AppLogo(
        height = 96.dp,
        modifier = Modifier.graphicsLayer {
            val scale = entrance.value * pulse
            scaleX = scale
            scaleY = scale
            alpha = entrance.value.coerceIn(0f, 1f)
        },
    )
}

// Étape 2 : mosaïque de vraies vignettes. Sans catalogue, des rectangles aux couleurs de l'application
// de même forme (ADR-028, D10) : jamais d'erreur ni de bouton « Réessayer » sur cette étape.
@Composable
private fun VideoMosaic(videos: List<Video>) {
    val rows = 4
    val columns = 2
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(rows) { row ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(columns) { column ->
                    val slot = row * columns + column
                    Thumbnail(
                        video = videos.getOrNull(slot),
                        isAccent = slot % 5 == 2,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .cascadeIn(slot / 2),
                    )
                }
            }
        }
    }
}

// Étape 3 : quelques miniatures verticales de Shorts, qui montent et descendent doucement.
@Composable
private fun ShortsRow(shorts: List<Video>) {
    val reduceMotion = LocalReduceMotion.current
    val drift by rememberInfiniteTransition(label = "shorts-drift").animateFloat(
        initialValue = -1f,
        targetValue = if (reduceMotion) -1f else 1f,
        animationSpec = infiniteRepeatable(tween(2_400, easing = LinearEasing), RepeatMode.Reverse),
        label = "shorts-drift-value",
    )
    val offset = with(LocalDensity.current) { 14.dp.toPx() }
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { slot ->
            // Les colonnes voisines vont en sens contraire : l'effet de défilement.
            val direction = if (slot == 1) -1f else 1f
            Thumbnail(
                video = shorts.getOrNull(slot),
                isAccent = slot == 1,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(9f / 16f)
                    .graphicsLayer { translationY = drift * offset * direction }
                    .cascadeIn(slot),
            )
        }
    }
}

@Composable
private fun Thumbnail(video: Video?, isAccent: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    if (video != null) {
        VideoThumbnailImage(
            url = video.thumbnailUrl,
            isPremiumVideo = video.isPremium,
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    } else {
        Box(
            modifier = modifier
                .clip(shape)
                .background(if (isAccent) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

@Composable
private fun NotificationsVisual() {
    val reduceMotion = LocalReduceMotion.current
    val swing by rememberInfiniteTransition(label = "bell").animateFloat(
        initialValue = -12f,
        targetValue = if (reduceMotion) 0f else 12f,
        animationSpec = infiniteRepeatable(tween(450, easing = Motion.Easing), RepeatMode.Reverse),
        label = "bell-swing",
    )
    Box(
        modifier = Modifier
            .size(112.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_notifications_active),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .size(56.dp)
                .rotate(swing),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Choix

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterestChoices(state: OnboardingUiState, required: Int, viewModel: OnboardingViewModel) {
    when (val categories = state.categories) {
        OnboardingCategories.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        }
        // Catégories injoignables : « Réessayer », ou « Passer » comme sur toute étape.
        is OnboardingCategories.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = categories.error.asText(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = viewModel::loadCategories) { Text(stringResource(R.string.retry)) }
        }
        is OnboardingCategories.Loaded -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.categories.forEach { category ->
                    FilterChip(
                        selected = category.id in state.selectedInterests,
                        onClick = { viewModel.toggleInterest(category.id) },
                        label = { Text(category.name) },
                    )
                }
            }
            if (required > 0) {
                Text(
                    text = stringResource(R.string.onboarding_interests_count, state.selectedInterests.size.coerceAtMost(required), required),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun SingleChoice(kind: StepKind.SingleChoice, state: OnboardingUiState, onSelect: (VideoFormat) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        kind.options.forEach { option ->
            val selected = state.preferredFormat?.name == option.value
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        BorderStroke(2.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
                        RoundedCornerShape(16.dp),
                    )
                    .clickable { VideoFormat.entries.firstOrNull { it.name == option.value }?.let(onSelect) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(option.label),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Chargement et récapitulatif

// Animation propre à l'étape : un anneau qui tourne autour du logo, et une jauge qui se remplit pendant
// toute la durée. Elle avance seule à la fin ; aucun bouton (ADR-028, D9).
@Composable
private fun LoadingStep(step: OnboardingStep, kind: StepKind.Loading, onDone: () -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    val fill = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        fill.animateTo(1f, tween(kind.durationMs.toInt(), easing = LinearEasing))
        onDone()
    }
    val spin by rememberInfiniteTransition(label = "loading-spin").animateFloat(
        initialValue = 0f,
        targetValue = if (reduceMotion) 0f else 360f,
        animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing)),
        label = "loading-spin-value",
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
            CircularProgressIndicator(
                progress = { 0.28f },
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(spin),
                strokeWidth = 5.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            AppLogo(height = 56.dp)
        }
        Text(
            text = stringResource(step.title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 32.dp).cascadeIn(1),
        )
        Box(
            modifier = Modifier
                .padding(top = 24.dp)
                .width(200.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fill.value)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Recap(kind: StepKind.Recap, state: OnboardingUiState) {
    val categories = (state.categories as? OnboardingCategories.Loaded)?.categories.orEmpty()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        kind.keys.forEach { key ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(
                        when (key) {
                            AnswerKey.INTERESTS -> R.string.onboarding_recap_interests
                            AnswerKey.PREFERRED_FORMAT -> R.string.onboarding_recap_format
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when (key) {
                    AnswerKey.INTERESTS -> {
                        val names = categories.filter { it.id in state.selectedInterests }.map { it.name }
                        if (names.isEmpty()) {
                            RecapNone()
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                names.forEach { name ->
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                    AnswerKey.PREFERRED_FORMAT -> {
                        val label = when (state.preferredFormat) {
                            VideoFormat.LONG -> R.string.onboarding_format_videos
                            VideoFormat.SHORT -> R.string.onboarding_format_shorts
                            null -> null
                        }
                        if (label == null) {
                            RecapNone()
                        } else {
                            Text(
                                text = stringResource(label),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecapNone() {
    Text(
        text = stringResource(R.string.onboarding_recap_none),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---------------------------------------------------------------------------------------------
// Écrans existants hébergés comme contenu d'une étape (ADR-028, D5)

// Inscription, ou connexion via « J'ai déjà un compte ». Les écrans sont recréés à chaque entrée
// dans l'étape (clé [visit]) : un ViewModel lié à l'activité garderait « inscrit » d'une visite à l'autre.
@Composable
private fun AccountStep(visit: Int, viewModel: OnboardingViewModel) {
    val stepId = "account"
    var showLogin by rememberSaveable { mutableStateOf(false) }
    if (showLogin) {
        LoginScreen(
            onLoggedIn = { viewModel.advanceAutomatically(stepId) },
            onRegisterClick = { showLogin = false },
            onContinueAsVisitor = viewModel::skip,
            viewModel = hiltViewModel(key = "onboarding_login_$visit"),
        )
    } else {
        RegisterScreen(
            onRegistered = { viewModel.advanceAutomatically(stepId) },
            onLoginClick = { showLogin = true },
            onContinueAsVisitor = viewModel::skip,
            viewModel = hiltViewModel(key = "onboarding_register_$visit"),
        )
    }
}

// Paywall existant, même test A/B et mêmes événements. `paywall_dismissed` part explicitement quand
// on quitte l'étape, par n'importe quel chemin : Suivant, Passer, retour, arrière-plan (D5).
@Composable
private fun PaywallStep(visit: Int, viewModel: OnboardingViewModel) {
    val paywall: PaywallViewModel = hiltViewModel(key = "onboarding_paywall_$visit")
    DisposableEffect(paywall) { onDispose { paywall.onLeft() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { paywall.onLeft() }
    PaywallScreen(
        onClose = viewModel::skip,
        // Un visiteur ne peut pas acheter sans compte : retour à l'étape d'inscription (ADR-014).
        onLoginRequired = viewModel::back,
        onPurchased = { viewModel.advanceAutomatically("paywall") },
        source = PaywallSource.Onboarding,
        embedded = true,
        viewModel = paywall,
    )
}
