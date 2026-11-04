package com.youtubeclone.app.presentation.ads

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.VideoAd
import com.youtubeclone.app.presentation.common.formatDuration

/**
 * Carte sponsorisée en haut du fil (ADR-021), pour les comptes non Premium : miniature en grand,
 * mention « Sponsorisé » et titre de la publicité. Un appui ouvre le site de l'annonceur dans le
 * navigateur ([onClick], pour la mesure) ; le bouton « Lecture » de la vignette lance l'annonce en
 * plein lecteur ([onPlay]) : deux gestes séparés. Sous la carte, un lien discret
 * « Supprimer les publicités » ouvre l'offre Premium.
 * [onShown] : appelé à l'affichage de la carte, pour la mesure.
 */
@Composable
fun SponsoredAdCard(
    ad: VideoAd,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onRemoveAdsClick: () -> Unit,
    onShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(ad.id) { onShown() }
    val uriHandler = LocalUriHandler.current
    val destination = ad.destinationUrl
    // Sans site enregistré, la carte lance l'annonce.
    val openSite = if (destination != null) {
        {
            onClick()
            uriHandler.openUri(destination)
        }
    } else {
        onPlay
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.clickable(onClick = openSite, role = Role.Button)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = ad.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                // Bouton « Lecture » : lance l'annonce, sans ouvrir le site.
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .clickable(onClick = onPlay, role = Role.Button)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_play),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.ad_play),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                }
                Text(
                    text = formatDuration(ad.durationSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = ad.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.ad_sponsored),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                if (destination != null) {
                    Icon(
                        painter = painterResource(R.drawable.ic_open_in_new),
                        contentDescription = stringResource(R.string.ad_visit_site),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        // Lien discret, pas un bouton.
        Text(
            text = stringResource(R.string.ad_remove),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clickable(onClick = onRemoveAdsClick, role = Role.Button)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}
