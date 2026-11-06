package com.youtubeclone.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.Video

// Aides d'accessibilité partagées (ADR-030). Une carte qui se lit comme un tout porte UNE phrase,
// composée ici, au lieu d'être lue élément par élément.

/**
 * Phrase lue par TalkBack pour une carte vidéo : titre, chaîne, vues, date, durée dite à voix
 * haute, et la mention Premium (réservée si le compte n'a pas l'abonnement).
 */
@Composable
fun videoAccessibilityLabel(video: Video): String {
    val base = stringResource(
        R.string.a11y_video_card,
        video.title,
        video.channel.name,
        formatViewCount(video.viewCount),
        formatRelativeDate(video.publishedAt),
        formatSpokenDuration(video.durationSeconds),
    )
    return when {
        !video.isPremium -> base
        LocalIsPremiumViewer.current -> stringResource(R.string.a11y_video_card_premium, base)
        else -> stringResource(R.string.a11y_video_card_locked, base)
    }
}

/** Police du système très agrandie (ADR-030) : les écrans laissent alors plus de lignes aux textes. */
@Composable
fun isLargeText(): Boolean = LocalDensity.current.fontScale > 1.3f
