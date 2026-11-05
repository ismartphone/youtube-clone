package com.youtubeclone.app.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.Channel
import com.youtubeclone.app.domain.model.Video
import com.youtubeclone.app.domain.model.VideoFormat
import com.youtubeclone.app.presentation.common.ChannelAvatar
import com.youtubeclone.app.presentation.common.PremiumBadge
import com.youtubeclone.app.presentation.common.VideoThumbnailImage
import com.youtubeclone.app.presentation.common.formatDuration
import com.youtubeclone.app.presentation.common.formatRelativeDate
import com.youtubeclone.app.presentation.common.formatViewCount
import com.youtubeclone.app.presentation.common.minTouchTarget
import com.youtubeclone.app.presentation.common.videoAccessibilityLabel
import com.youtubeclone.app.presentation.theme.YoutubeCloneTheme
import java.time.Instant

/**
 * Carte d'une vidéo : miniature 16/9 pleine largeur avec la durée, puis l'avatar de la chaîne
 * et, à sa droite, le titre (deux lignes au plus) et « chaîne · vues · date ».
 * Pas de bordure ni d'ombre : l'espacement entre les cartes est porté par la liste.
 * [showChannelAvatar] à false sur la page d'une chaîne, où l'avatar serait répété sur chaque carte.
 * Avec [onChannelClick], l'avatar et le nom de la chaîne ouvrent sa page.
 * TalkBack (ADR-030) : la carte est UN seul arrêt, lu en une phrase (titre, chaîne, vues, date, durée) ;
 * l'accès à la chaîne devient une action personnalisée de ce même arrêt, pas un arrêt de plus.
 */
@Composable
fun VideoCard(
    video: Video,
    modifier: Modifier = Modifier,
    onChannelClick: (() -> Unit)? = null,
    showChannelAvatar: Boolean = true,
) {
    val label = videoAccessibilityLabel(video)
    val openChannelLabel = stringResource(R.string.a11y_open_channel, video.channel.name)
    Column(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = label
            if (onChannelClick != null) {
                customActions = listOf(CustomAccessibilityAction(openChannelLabel) { onChannelClick(); true })
            }
        },
    ) {
        // Les enfants sont masqués à TalkBack : la phrase de la carte les remplace (le toucher, lui, marche).
        VideoThumbnail(video, Modifier.fillMaxWidth().clearAndSetSemantics {})
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp).clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (showChannelAvatar) {
                // Zone cliquable de 48 dp (ADR-030) : l'avatar garde ses 36 dp en haut à gauche, et le
                // texte reste au même endroit (48 dp de zone, sans écart, au lieu de 36 dp + 12 dp d'écart).
                Box(
                    modifier = Modifier
                        .minTouchTarget()
                        .clip(CircleShape)
                        .then(if (onChannelClick != null) Modifier.clickable(onClick = onChannelClick) else Modifier),
                    contentAlignment = Alignment.TopStart,
                ) {
                    ChannelAvatar(channel = video.channel, modifier = Modifier.size(36.dp).clip(CircleShape))
                }
            }
            val metaColor = MaterialTheme.colorScheme.onSurfaceVariant
            // Police très agrandie (ADR-030) : plus de lignes pour le titre et la ligne d'informations, au lieu de « … ».
            val largeText = LocalDensity.current.fontScale > 1.3f
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = if (largeText) 4 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildAnnotatedString {
                        // Seul le nom de la chaîne est cliquable dans la ligne d'informations.
                        // Lien sans le style par défaut (rouge souligné) : même gris que le reste de la ligne.
                        if (onChannelClick != null) {
                            val link = LinkAnnotation.Clickable(
                                tag = "channel",
                                styles = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.None, color = metaColor)),
                            ) { onChannelClick() }
                            withLink(link) {
                                append(video.channel.name)
                            }
                        } else {
                            append(video.channel.name)
                        }
                        append(" · ${formatViewCount(video.viewCount)} · ${formatRelativeDate(video.publishedAt)}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = metaColor,
                    maxLines = if (largeText) 3 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// Miniature 16/9, durée en bas à droite sur fond noir translucide. La largeur vient de [modifier]
// (pleine largeur dans le fil, colonne de gauche dans les vidéos suggérées).
@Composable
internal fun VideoThumbnail(video: Video, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        // Floutée avec un cadenas pour un compte sans Premium (ADR-026).
        VideoThumbnailImage(video.thumbnailUrl, video.isPremium, Modifier.matchParentSize())
        // Vidéo exclusive : le badge prévient avant d'ouvrir ; l'accès reste vérifié par le serveur.
        if (video.isPremium) PremiumBadge(Modifier.align(Alignment.TopStart).padding(8.dp))
        Text(
            text = formatDuration(video.durationSeconds),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

/** Espacement vertical entre deux cartes : généreux, il remplace bordures et ombres. */
val VideoCardSpacing = 28.dp

/** Vidéo fictive pour les aperçus. */
internal fun previewVideo(id: String, title: String, channel: String, views: Int, daysAgo: Long) = Video(
    id = id,
    title = title,
    thumbnailUrl = "",
    durationSeconds = 52 + views % 600,
    format = VideoFormat.LONG,
    isPremium = false,
    viewCount = views,
    publishedAt = Instant.now().minusSeconds(daysAgo * 24 * 3600),
    channel = Channel(id = "c-$id", name = channel, handle = channel.lowercase(), avatarUrl = null),
    category = null,
)

@Preview(showBackground = true, backgroundColor = 0xFF0F0F0F)
@Composable
private fun VideoCardPreview() {
    YoutubeCloneTheme {
        VideoCard(
            video = previewVideo("1", "Sintel, la bande-annonce de ce court métrage d'animation libre", "Studio Libre", 1_234, 3),
            onChannelClick = {},
        )
    }
}
