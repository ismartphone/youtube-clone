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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.youtubeclone.app.domain.model.Channel
import com.youtubeclone.app.domain.model.Video
import com.youtubeclone.app.domain.model.VideoFormat
import com.youtubeclone.app.presentation.common.ChannelAvatar
import com.youtubeclone.app.presentation.common.formatDuration
import com.youtubeclone.app.presentation.common.formatRelativeDate
import com.youtubeclone.app.presentation.common.formatViewCount
import com.youtubeclone.app.presentation.theme.YoutubeCloneTheme
import java.time.Instant

/**
 * Carte d'une vidéo : miniature 16/9 pleine largeur avec la durée, puis l'avatar de la chaîne
 * et, à sa droite, le titre (deux lignes au plus) et « chaîne · vues · date ».
 * Pas de bordure ni d'ombre : l'espacement entre les cartes est porté par la liste.
 * [showChannelAvatar] à false sur la page d'une chaîne, où l'avatar serait répété sur chaque carte.
 * Avec [onChannelClick], l'avatar et le nom de la chaîne ouvrent sa page.
 */
@Composable
fun VideoCard(
    video: Video,
    modifier: Modifier = Modifier,
    onChannelClick: (() -> Unit)? = null,
    showChannelAvatar: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Thumbnail(video)
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showChannelAvatar) {
                ChannelAvatar(
                    channel = video.channel,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .then(if (onChannelClick != null) Modifier.clickable(onClick = onChannelClick) else Modifier),
                )
            }
            val metaColor = MaterialTheme.colorScheme.onSurfaceVariant
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// Miniature 16/9 pleine largeur, durée en bas à droite sur fond noir translucide.
@Composable
private fun Thumbnail(video: Video) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
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
