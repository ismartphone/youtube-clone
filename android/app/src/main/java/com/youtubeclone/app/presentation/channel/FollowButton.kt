package com.youtubeclone.app.presentation.channel

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.youtubeclone.app.R
import com.youtubeclone.app.presentation.theme.LocalReduceMotion
import com.youtubeclone.app.presentation.theme.Motion

/**
 * S'abonner (bouton clair, comme sur YouTube) ou Abonné (bouton discret).
 * Un seul bouton dont la couleur et la largeur changent en douceur entre les deux états.
 * [compact] : version réduite posée sur une vidéo (onglet Shorts).
 */
@Composable
fun FollowButton(
    state: FollowUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val reduceMotion = LocalReduceMotion.current
    val duration = if (reduceMotion) Motion.REDUCED_FADE_MS else Motion.FADE_MS
    val container by animateColorAsState(
        targetValue = if (state.isFollowing) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.onBackground,
        animationSpec = tween(duration, easing = Motion.Easing),
        label = "followContainer",
    )
    val content by animateColorAsState(
        targetValue = if (state.isFollowing) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.background,
        animationSpec = tween(duration, easing = Motion.Easing),
        label = "followContent",
    )
    Button(
        onClick = onClick,
        enabled = !state.isPending,
        // Pendant l'appel au serveur, le bouton garde son apparence : pas de clignotement gris.
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container,
            disabledContentColor = content,
        ),
        contentPadding = if (compact) PaddingValues(horizontal = 12.dp) else ButtonDefaults.ContentPadding,
        modifier = modifier
            .then(if (compact) Modifier.height(32.dp) else Modifier)
            .then(if (reduceMotion) Modifier else Modifier.animateContentSize(tween(duration, easing = Motion.Easing))),
    ) {
        Text(
            text = stringResource(if (state.isFollowing) R.string.channel_following else R.string.channel_follow),
            style = if (compact) MaterialTheme.typography.labelMedium else LocalTextStyle.current,
        )
    }
}
