package com.youtubeclone.app.presentation.ads

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.VideoAd

// Carte sur fond clair, comme la maquette (android/maquettes/publicité.home.jpeg).
private val CardBackground = Color(0xFFF2F2F2)
private val CardText = Color(0xFF0F0F0F)
private val CardSecondaryText = Color(0xFF606060)
private val WatchButtonBackground = Color(0xFFE5E5E5)

// Fonds de l'initiale quand l'annonceur n'a pas de logo, choisis d'après son nom.
private val InitialBackgrounds = listOf(
    Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFE53935), Color(0xFF8E24AA), Color(0xFFF4511E), Color(0xFF00897B),
)

/**
 * Bandeau sponsorisé en haut du fil (ADR-021), pour les comptes non Premium : la vidéo de
 * l'annonce en boucle et en sourdine, avec un bouton haut-parleur ; dessous, une carte claire
 * (logo ou initiale, titre, description, « Sponsorisé ») et deux boutons : « Regarder » ouvre
 * l'annonce en plein lecteur avec le son, le bouton principal ouvre le site de l'annonceur. Sous la
 * carte, le lien discret « Supprimer les publicités ».
 * [onSiteClick] et [onWatch] servent à la mesure et à la navigation ; le site s'ouvre ici.
 */
@Composable
fun SponsoredAdCard(
    ad: VideoAd,
    player: Player,
    muted: Boolean,
    coverWithThumbnail: Boolean,
    onToggleMute: () -> Unit,
    onWatch: () -> Unit,
    onSiteClick: () -> Unit,
    onRemoveAdsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
        ) {
            PreviewPlayer(player, Modifier.fillMaxSize())
            // La vue vidéo est noire avant la première image : la miniature la recouvre jusque-là,
            // et pour de bon si la vidéo est abandonnée (erreur de lecture, aucune image à temps).
            if (coverWithThumbnail) {
                AsyncImage(
                    model = ad.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(onClick = onToggleMute, role = Role.Button),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_up),
                    contentDescription = stringResource(if (muted) R.string.ad_unmute else R.string.ad_mute),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBackground)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AdvertiserLogo(ad)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = ad.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = CardText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ad.description?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = CardSecondaryText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = stringResource(R.string.ad_sponsored),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = CardText,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onWatch,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = WatchButtonBackground, contentColor = CardText),
                ) {
                    Text(stringResource(R.string.ad_watch))
                }
                val destination = ad.destinationUrl
                if (destination != null) {
                    Button(
                        onClick = {
                            onSiteClick()
                            uriHandler.openUri(destination)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = CardText, contentColor = Color.White),
                    ) {
                        Text(ad.ctaLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
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

// Logo carré de l'annonceur, ou son initiale sur un fond de couleur à défaut.
@Composable
private fun AdvertiserLogo(ad: VideoAd) {
    val shape = RoundedCornerShape(8.dp)
    val logo = ad.logoUrl
    if (logo != null) {
        AsyncImage(
            model = logo,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(shape).background(Color.White),
        )
    } else {
        val name = ad.advertiser ?: ad.title
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(shape)
                .background(InitialBackgrounds[Math.floorMod(name.hashCode(), InitialBackgrounds.size)]),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.take(1).uppercase(),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }
    }
}

// Vidéo sans contrôles, qui remplit le cadre.
@OptIn(UnstableApi::class)
@Composable
private fun PreviewPlayer(player: Player, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                this.player = player
            }
        },
        onRelease = { view -> view.player = null },
        modifier = modifier,
    )
}
