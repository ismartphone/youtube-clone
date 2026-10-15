package com.youtubeclone.app.presentation.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.Channel
import com.youtubeclone.app.domain.model.VideoDetail
import com.youtubeclone.app.presentation.common.ErrorState
import com.youtubeclone.app.presentation.common.LoadingState
import com.youtubeclone.app.presentation.common.formatRelativeDate
import com.youtubeclone.app.presentation.common.formatViewCount

@Composable
fun WatchScreen(
    modifier: Modifier = Modifier,
    viewModel: WatchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Application en arrière-plan : la lecture se met en pause, sans libérer le lecteur.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.pause() }

    when (val state = uiState) {
        WatchUiState.Loading -> LoadingState(modifier)
        is WatchUiState.Error -> ErrorState(state.error, viewModel::load, modifier)
        is WatchUiState.Success -> WatchContent(state.detail, viewModel.player, modifier)
    }
}

@Composable
private fun WatchContent(detail: VideoDetail, player: Player, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (detail.videoUrl != null) {
                VideoPlayer(player = player, modifier = Modifier.fillMaxSize())
            } else {
                // Le serveur n'a pas donné l'URL : accès refusé, par exemple vidéo Premium.
                Text(
                    text = stringResource(R.string.watch_premium_only),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        VideoInfo(detail)
    }
}

// Vue native de Media3, avec ses contrôles intégrés.
@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(player: Player, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                this.player = player
                // Indicateur de chargement pendant la mise en mémoire tampon.
                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            }
        },
        // La vue est détachée du lecteur quand elle quitte l'écran ; le lecteur, lui, reste au ViewModel.
        onRelease = { view -> view.player = null },
        modifier = modifier,
    )
}

@Composable
private fun VideoInfo(detail: VideoDetail) {
    val video = detail.video
    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "${formatViewCount(video.viewCount)} · ${formatRelativeDate(video.publishedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ChannelRow(video.channel)
        detail.description?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                    .padding(12.dp),
            )
        }
    }
}

@Composable
private fun ChannelRow(channel: Channel) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ChannelAvatar(channel, Modifier.size(36.dp))
        Text(
            text = channel.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

// Avatar de la chaîne, ou à défaut un cercle avec l'initiale de son nom.
@Composable
private fun ChannelAvatar(channel: Channel, modifier: Modifier = Modifier) {
    if (channel.avatarUrl != null) {
        AsyncImage(
            model = channel.avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(CircleShape),
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = channel.name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
