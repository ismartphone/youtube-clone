package com.youtubeclone.app.presentation.live

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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.Live
import com.youtubeclone.app.presentation.common.ChannelAvatar
import com.youtubeclone.app.presentation.theme.YoutubeRed

/** Pastille rouge « EN DIRECT » (ADR-029), sur la miniature d'un live et sur son écran. */
@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.live_badge),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = Color.White,
        modifier = modifier
            .background(YoutubeRed, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Nombre de spectateurs sur fond noir translucide, lisible sur n'importe quelle image. */
@Composable
fun ViewerCountPill(count: Int, modifier: Modifier = Modifier) {
    Text(
        text = pluralStringResource(R.plurals.live_viewers, count, count),
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * Carte d'un live en cours, en tête du fil d'accueil et sur la page de la chaîne : miniature de la
 * vidéo du flux avec la pastille « EN DIRECT » et le nombre de spectateurs, puis l'avatar de la
 * chaîne, le titre du live et le nom de la chaîne. Un appui ouvre le live. Pour TalkBack (ADR-030) :
 * un seul arrêt, « En direct : titre, chaîne, N spectateurs ».
 */
@Composable
fun LiveCard(live: Live, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(
        R.string.a11y_live_card,
        live.title,
        live.channel.name,
        pluralStringResource(R.plurals.live_viewers, live.viewerCount, live.viewerCount),
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        // Enfants masqués à TalkBack : la phrase de la carte les remplace.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clearAndSetSemantics {},
        ) {
            AsyncImage(
                model = live.stream?.thumbnailUrl,
                // Décoratif (ADR-030) : le sens est porté par le texte voisin, TalkBack ignore l'image.
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            LiveBadge(Modifier.align(Alignment.TopStart).padding(8.dp))
            ViewerCountPill(live.viewerCount, Modifier.align(Alignment.BottomEnd).padding(8.dp))
        }
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp).clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChannelAvatar(channel = live.channel, modifier = Modifier.size(36.dp).clip(CircleShape))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = live.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = live.channel.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
