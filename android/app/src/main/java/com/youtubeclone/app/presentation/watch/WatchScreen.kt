package com.youtubeclone.app.presentation.watch

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.Channel
import com.youtubeclone.app.domain.model.VideoDetail
import com.youtubeclone.app.presentation.channel.FollowButton
import com.youtubeclone.app.presentation.channel.FollowUiState
import com.youtubeclone.app.presentation.common.ChannelAvatar
import com.youtubeclone.app.presentation.common.ErrorState
import com.youtubeclone.app.presentation.common.ErrorToast
import com.youtubeclone.app.presentation.common.LoadingState
import com.youtubeclone.app.presentation.common.formatCompactCount
import com.youtubeclone.app.presentation.common.formatFollowerCount
import com.youtubeclone.app.presentation.common.formatRelativeDate
import com.youtubeclone.app.presentation.common.formatViewCount

@Composable
fun WatchScreen(
    onChannelClick: (channelId: String) -> Unit,
    onLoginRequired: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WatchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val followState by viewModel.follow.state.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val commentsState by viewModel.comments.state.collectAsStateWithLifecycle()
    ErrorToast(viewModel.follow.errors)
    ErrorToast(viewModel.errors)
    ErrorToast(viewModel.comments.errors)

    // Application en arrière-plan : la lecture se met en pause, sans libérer le lecteur.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.pause() }

    // Connexion temps réel ouverte tant que l'écran est visible : fermée quand on le quitte
    // (autre écran, application en arrière-plan), rouverte au retour.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.collectLiveUpdates()
        }
    }

    // Visiteur : la connexion est demandée au moment d'agir.
    fun requireLogin(action: () -> Unit) = if (isLoggedIn) action() else onLoginRequired()

    when (val state = uiState) {
        WatchUiState.Loading -> LoadingState(modifier)
        is WatchUiState.Error -> ErrorState(state.error, viewModel::load, modifier)
        is WatchUiState.Success -> Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            PlayerBox(state.detail, viewModel.player)
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                VideoInfo(
                    detail = state.detail,
                    stats = stats,
                    followState = followState,
                    onChannelClick = { onChannelClick(state.detail.video.channel.id) },
                    onFollowClick = { requireLogin(viewModel.follow::toggle) },
                    onLikeClick = { requireLogin(viewModel::toggleLike) },
                )
                CommentsSection(
                    state = commentsState,
                    isLoggedIn = isLoggedIn,
                    onDraftChange = viewModel.comments::onDraftChange,
                    onPost = viewModel.comments::post,
                    onLoginRequired = onLoginRequired,
                    onLoadMore = viewModel.comments::loadMore,
                    onRetry = viewModel.comments::loadFirstPage,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun PlayerBox(detail: VideoDetail, player: Player) {
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
private fun VideoInfo(
    detail: VideoDetail,
    stats: LiveStats?,
    followState: FollowUiState?,
    onChannelClick: () -> Unit,
    onFollowClick: () -> Unit,
    onLikeClick: () -> Unit,
) {
    val video = detail.video
    // Compteur de vues en direct ; celui du chargement tant que les statistiques ne sont pas là.
    val viewCount = stats?.viewCount ?: video.viewCount
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "${formatViewCount(viewCount)} · ${formatRelativeDate(video.publishedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ChannelRow(video.channel, followState, onChannelClick, onFollowClick)
        if (stats != null) {
            LikeButton(stats, onLikeClick)
        }
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

// Bouton qui bascule : icône rouge quand la vidéo est aimée, avec le nombre de « j'aime ».
@Composable
private fun LikeButton(stats: LiveStats, onClick: () -> Unit) {
    val stateText = stringResource(if (stats.isLiked) R.string.watch_liked else R.string.watch_not_liked)
    FilledTonalButton(
        onClick = onClick,
        enabled = !stats.likePending,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ),
        modifier = Modifier.semantics {
            role = Role.Switch
            stateDescription = stateText
        },
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_like),
            contentDescription = stringResource(R.string.watch_like),
            tint = if (stats.isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(18.dp),
        )
        Text(text = formatCompactCount(stats.likeCount), modifier = Modifier.padding(start = 8.dp))
    }
}

// Avatar et nom ouvrent la page de la chaîne ; le bouton S'abonner apparaît une fois l'état connu.
@Composable
private fun ChannelRow(
    channel: Channel,
    followState: FollowUiState?,
    onChannelClick: () -> Unit,
    onFollowClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onChannelClick),
        ) {
            ChannelAvatar(channel, Modifier.size(36.dp))
            Column {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (followState != null) {
                    Text(
                        text = formatFollowerCount(followState.followerCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (followState != null && followState.canFollow) {
            FollowButton(state = followState, onClick = onFollowClick)
        }
    }
}
