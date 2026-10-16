package com.youtubeclone.app.presentation.channel

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.youtubeclone.app.R
import com.youtubeclone.app.presentation.theme.LocalReduceMotion
import com.youtubeclone.app.presentation.theme.Motion

/**
 * S'abonner (bouton clair, comme sur YouTube) ou Abonné (bouton discret).
 * Un seul bouton dont la couleur et la largeur changent en douceur entre les deux états.
 */
@Composable
fun FollowButton(
    state: FollowUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
        modifier = if (reduceMotion) modifier else modifier.animateContentSize(tween(duration, easing = Motion.Easing)),
    ) {
        Text(stringResource(if (state.isFollowing) R.string.channel_following else R.string.channel_follow))
    }
}
