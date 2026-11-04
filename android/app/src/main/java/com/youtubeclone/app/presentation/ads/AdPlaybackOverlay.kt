package com.youtubeclone.app.presentation.ads

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.youtubeclone.app.R
import com.youtubeclone.app.presentation.common.formatDuration

private val AdYellow = Color(0xFFFFC107)
private val TextShadow = Shadow(color = Color.Black, offset = Offset(0f, 1f), blurRadius = 6f)

/**
 * Annonce en plein lecteur (ADR-021) : la vidéo de la publicité, la mention « Annonce » et le
 * temps restant, une barre de progression qui ne se déplace pas, puis « Ignorer » en bas à droite
 * au bout de 5 secondes. [onRemoveAds] : lien discret vers l'offre Premium, en simple texte et en
 * haut à droite, loin d'« Ignorer » ; null pour ne pas l'afficher.
 */
@Composable
fun AdPlaybackOverlay(
    state: AdPlaybackState,
    player: Player,
    onSkip: () -> Unit,
    onRemoveAds: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    // Aucun contrôle de lecture pendant l'annonce.
                    useController = false
                    this.player = player
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                }
            },
            onRelease = { view -> view.player = null },
            modifier = Modifier.fillMaxSize(),
        )
        if (onRemoveAds != null) {
            Text(
                text = stringResource(R.string.ad_remove),
                // Ombre portée : le lien reste lisible sur une image claire de l'annonce.
                style = MaterialTheme.typography.labelMedium.copy(shadow = TextShadow),
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clickable(onClick = onRemoveAds, role = Role.Button)
                    .padding(horizontal = 12.dp, vertical = 14.dp),
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.ad_label),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.Black,
                modifier = Modifier
                    .background(AdYellow, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Text(
                text = formatDuration(state.secondsLeft),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
        }
        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 14.dp)) {
            if (state.canSkip) {
                Row(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .clickable(onClick = onSkip, role = Role.Button)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.ad_skip),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_skip_next),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.ad_skip_in, state.secondsUntilSkip),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        // Progression seulement : la barre ne réagit pas au toucher.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(3.dp)
                .background(Color.White.copy(alpha = 0.3f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(state.progress)
                    .height(3.dp)
                    .background(AdYellow),
            )
        }
    }
}
