package com.youtubeclone.app.presentation.watch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.youtubeclone.app.domain.model.Video
import com.youtubeclone.app.presentation.common.VisibilityBadge
import com.youtubeclone.app.presentation.common.formatRelativeDate
import com.youtubeclone.app.presentation.common.formatViewCount
import com.youtubeclone.app.presentation.common.videoAccessibilityLabel
import com.youtubeclone.app.presentation.home.VideoThumbnail
import com.youtubeclone.app.presentation.home.previewVideo
import com.youtubeclone.app.presentation.theme.YoutubeCloneTheme

/**
 * Vidéo suggérée, format compact : miniature à gauche, titre et métadonnées à droite. Pour TalkBack
 * (ADR-030) : un seul arrêt, lu en une phrase (titre, chaîne, vues, date, durée).
 */
@Composable
fun SuggestedVideoCard(video: Video, modifier: Modifier = Modifier) {
    val label = videoAccessibilityLabel(video)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = label }
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VideoThumbnail(
            video = video,
            modifier = Modifier
                .width(160.dp)
                .clip(RoundedCornerShape(8.dp))
                .clearAndSetSemantics {},
        )
        // Masqué à TalkBack : la phrase de la carte le remplace.
        Column(modifier = Modifier.clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            VisibilityBadge(video.visibility)
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = video.channel.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${formatViewCount(video.viewCount)} · ${formatRelativeDate(video.publishedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F0F0F)
@Composable
private fun SuggestedVideoCardPreview() {
    YoutubeCloneTheme {
        SuggestedVideoCard(previewVideo("1", "Le ballet des méduses, plongée au milieu des méduses", "Studio Libre", 2_300, 7))
    }
}
