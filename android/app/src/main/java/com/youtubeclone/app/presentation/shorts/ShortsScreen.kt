package com.youtubeclone.app.presentation.shorts

import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.Video
import com.youtubeclone.app.presentation.common.ErrorState
import com.youtubeclone.app.presentation.common.LoadingState
import com.youtubeclone.app.presentation.common.MessageState

@Composable
fun ShortsScreen(
    modifier: Modifier = Modifier,
    viewModel: ShortsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Lecteur créé à l'affichage de l'onglet et libéré quand on le quitte.
    DisposableEffect(Unit) {
        viewModel.onShown()
        onDispose { viewModel.onHidden() }
    }
    // Arrière-plan : pause seulement, reprise au retour.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onBackground() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }

    when (val state = uiState) {
        ShortsUiState.Loading -> LoadingState(modifier)
        ShortsUiState.Empty -> MessageState(stringResource(R.string.shorts_empty), modifier)
        is ShortsUiState.Error -> ErrorState(state.error, viewModel::load, modifier)
        is ShortsUiState.Success -> ShortsPager(state.videos, viewModel, modifier)
    }
}

@Composable
private fun ShortsPager(videos: List<Video>, viewModel: ShortsViewModel, modifier: Modifier) {
    val pagerState = rememberPagerState(pageCount = { videos.size })
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    // Suivi permanent (pas seulement au premier plan) : la vue doit lâcher le lecteur dès sa libération.
    val sharedPlayer by viewModel.player.collectAsState()

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect(viewModel::onPageLeaving)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect(viewModel::onPageSettled)
    }

    VerticalPager(
        state = pagerState,
        key = { videos[it].id },
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) { page ->
        val isActive = page == playback.page
        ShortPage(
            video = videos[page],
            player = sharedPlayer.takeIf { isActive },
            pausedByUser = isActive && playback.pausedByUser,
            blockedReason = playback.blockedReason?.takeIf { isActive }?.let {
                if (it.code != null) stringResource(it.messageRes, it.code) else stringResource(it.messageRes)
            },
            onTap = viewModel::togglePlayPause,
        )
    }
}

@Composable
private fun ShortPage(
    video: Video,
    player: Player?,
    pausedByUser: Boolean,
    blockedReason: String?,
    onTap: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            ),
    ) {
        // Miniature visible tant que la première image de la vidéo n'est pas affichée.
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Une vue neuve pour chaque instance du lecteur, recréé après un passage en arrière-plan.
        if (player != null) key(player) { ShortPlayer(player, Modifier.fillMaxSize()) }

        if (blockedReason != null) {
            Text(
                text = blockedReason,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.7f), MaterialTheme.shapes.medium)
                    .padding(16.dp),
            )
        } else if (pausedByUser) {
            Icon(
                painter = painterResource(R.drawable.ic_play),
                contentDescription = stringResource(R.string.shorts_paused),
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    .padding(12.dp),
            )
        }

        ShortOverlay(video, Modifier.align(Alignment.BottomCenter))
    }
}

// Vue Media3 sans contrôles, qui remplit la page en recadrant la vidéo.
@OptIn(UnstableApi::class)
@Composable
private fun ShortPlayer(player: Player, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                // Fond transparent avant la première image : la miniature reste visible.
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                this.player = player
            }
        },
        // Détache la vue du lecteur partagé quand la page quitte l'écran.
        onRelease = { view -> view.player = null },
        modifier = modifier,
    )
}

@Composable
private fun ShortOverlay(video: Video, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
            .fillMaxWidth()
            // Dégradé pour garder le texte lisible sur une vidéo claire.
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
            .padding(start = 16.dp, end = 8.dp, top = 48.dp, bottom = 16.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = video.channel.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Actions affichées sans comportement pour l'instant.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ShortAction(R.drawable.ic_like, R.string.shorts_like)
            ShortAction(R.drawable.ic_comment, R.string.shorts_comments)
            ShortAction(R.drawable.ic_share, R.string.shorts_share)
        }
    }
}

@Composable
private fun ShortAction(@DrawableRes icon: Int, @StringRes label: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(48.dp)
                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                .padding(12.dp),
        )
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
