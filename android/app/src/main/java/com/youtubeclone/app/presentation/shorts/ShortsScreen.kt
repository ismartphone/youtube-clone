package com.youtubeclone.app.presentation.shorts

import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
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
import com.youtubeclone.app.presentation.channel.FollowButton
import com.youtubeclone.app.presentation.channel.FollowUiState
import com.youtubeclone.app.presentation.common.BackTopBar
import com.youtubeclone.app.presentation.common.ChannelAvatar
import com.youtubeclone.app.presentation.common.ErrorState
import com.youtubeclone.app.presentation.common.ErrorToast
import com.youtubeclone.app.presentation.common.LoadingState
import com.youtubeclone.app.presentation.common.MessageState
import com.youtubeclone.app.presentation.common.formatCompactCount
import com.youtubeclone.app.presentation.common.shareVideo
import com.youtubeclone.app.presentation.home.previewVideo
import com.youtubeclone.app.presentation.theme.LocalReduceMotion
import com.youtubeclone.app.presentation.theme.Motion
import com.youtubeclone.app.presentation.theme.YoutubeCloneTheme
import com.youtubeclone.app.presentation.watch.CommentsSheet

// Onglet Shorts d'après les maquettes (android/maquettes/shorts-01 et -05, captures iOS) :
// vidéo plein écran, colonne d'actions à droite, chaîne et titre en bas à gauche sur un dégradé.
// On reprend la mise en page et la hiérarchie, avec les conventions Material.

@Composable
fun ShortsScreen(
    onChannelClick: (channelId: String) -> Unit,
    onLoginRequired: () -> Unit,
    onPremiumClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Lecteur ouvert par-dessus un onglet (depuis une chaîne) : flèche de retour sur la vidéo.
    onBack: (() -> Unit)? = null,
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

    ErrorToast(viewModel.errors)
    ErrorToast(viewModel.follow.errors)

    Box(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            ShortsUiState.Loading -> LoadingState()
            ShortsUiState.Empty -> MessageState(stringResource(R.string.shorts_empty))
            is ShortsUiState.Error -> ErrorState(state.error, viewModel::load)
            is ShortsUiState.Success -> ShortsPager(state.videos, viewModel, onChannelClick, onLoginRequired, onPremiumClick, Modifier)
        }
        if (onBack != null) {
            BackTopBar(title = stringResource(R.string.tab_shorts), onBack = onBack, contentColor = Color.White)
        }
    }
}

@Composable
private fun ShortsPager(
    videos: List<Video>,
    viewModel: ShortsViewModel,
    onChannelClick: (channelId: String) -> Unit,
    onLoginRequired: () -> Unit,
    onPremiumClick: () -> Unit,
    modifier: Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { videos.size })
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    // Suivi permanent (pas seulement au premier plan) : la vue doit lâcher le lecteur dès sa libération.
    val sharedPlayer by viewModel.player.collectAsState()
    val likes by viewModel.likes.collectAsStateWithLifecycle()
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val followState by viewModel.follow.state.collectAsStateWithLifecycle()
    val followChannelId by viewModel.followChannelId.collectAsStateWithLifecycle()
    val commentsController by viewModel.comments.collectAsStateWithLifecycle()
    val commentsState = commentsController?.state?.collectAsStateWithLifecycle()?.value
    commentsController?.let { key(it) { ErrorToast(it.errors) } }

    val context = LocalContext.current
    var showComments by rememberSaveable { mutableStateOf(false) }

    // Visiteur : la connexion est demandée au moment d'agir.
    fun requireLogin(action: () -> Unit) = if (isLoggedIn) action() else onLoginRequired()

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
        val video = videos[page]
        val isActive = page == playback.page
        ShortPage(
            video = video,
            player = sharedPlayer.takeIf { isActive },
            pausedByUser = isActive && playback.pausedByUser,
            blockedReason = playback.blockedReason?.takeIf { isActive }?.let {
                if (it.code != null) stringResource(it.messageRes, it.code) else stringResource(it.messageRes)
            },
            premiumRequired = isActive && playback.premiumRequired,
            likes = likes[video.id],
            // Le nombre de commentaires n'est connu que pour le Short visible.
            commentCount = commentsState?.takeIf { isActive && !it.isLoading && it.error == null }?.total,
            followState = followState?.takeIf { followChannelId == video.channel.id },
            onTap = viewModel::togglePlayPause,
            onLikeClick = { requireLogin { viewModel.toggleLike(video.id) } },
            onCommentsClick = { showComments = true },
            onShareClick = { shareVideo(context, video) },
            onChannelClick = { onChannelClick(video.channel.id) },
            onFollowClick = { requireLogin(viewModel.follow::toggle) },
            onPremiumClick = onPremiumClick,
        )
    }

    // Même panneau que l'écran de lecture.
    val controller = commentsController
    if (showComments && controller != null && commentsState != null) {
        CommentsSheet(
            state = commentsState,
            controller = controller,
            isLoggedIn = isLoggedIn,
            onLoginRequired = onLoginRequired,
            onDismiss = { showComments = false },
        )
    }
}

@Composable
private fun ShortPage(
    video: Video,
    player: Player?,
    pausedByUser: Boolean,
    blockedReason: String?,
    premiumRequired: Boolean,
    likes: ShortLikes?,
    commentCount: Int?,
    followState: FollowUiState?,
    onTap: () -> Unit,
    onLikeClick: () -> Unit,
    onCommentsClick: () -> Unit,
    onShareClick: () -> Unit,
    onChannelClick: () -> Unit,
    onFollowClick: () -> Unit,
    onPremiumClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
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

        // Dégradé sombre du bas vers le haut : texte et icônes restent lisibles sur une image claire.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(320.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))),
        )

        if (blockedReason != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp)
                    .background(Color.Black.copy(alpha = 0.7f), MaterialTheme.shapes.medium)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = blockedReason,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                if (premiumRequired) {
                    Button(
                        onClick = onPremiumClick,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    ) {
                        Text(stringResource(R.string.premium_discover), fontWeight = FontWeight.Bold)
                    }
                }
            }
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

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, bottom = 16.dp),
        ) {
            ShortInfo(video, followState, onChannelClick, onFollowClick, Modifier.weight(1f))
            ShortActions(video, likes, commentCount, onLikeClick, onCommentsClick, onShareClick, onChannelClick)
        }
    }
}

// En bas à gauche : nom de la chaîne et S'abonner compact, puis le titre sur deux lignes.
@Composable
private fun ShortInfo(
    video: Video,
    followState: FollowUiState?,
    onChannelClick: () -> Unit,
    onFollowClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = video.channel.name,
                style = OverlayTextStyle.merge(MaterialTheme.typography.titleSmall).copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clickable(onClick = onChannelClick, role = Role.Button),
            )
            if (followState != null && followState.canFollow) {
                FollowButton(state = followState, onClick = onFollowClick, compact = true)
            }
        }
        Text(
            text = video.title,
            style = OverlayTextStyle.merge(MaterialTheme.typography.bodyMedium),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// Colonne d'actions, de bas en haut : J'aime, Commentaires, Partager, puis l'avatar de la chaîne.
@Composable
private fun ShortActions(
    video: Video,
    likes: ShortLikes?,
    commentCount: Int?,
    onLikeClick: () -> Unit,
    onCommentsClick: () -> Unit,
    onShareClick: () -> Unit,
    onChannelClick: () -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val likeTint by animateColorAsState(
        targetValue = if (likes?.isLiked == true) MaterialTheme.colorScheme.primary else Color.White,
        animationSpec = tween(if (reduceMotion) Motion.REDUCED_FADE_MS else Motion.SHORT_MS),
        label = "shortLikeTint",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ChannelAvatar(
            channel = video.channel,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .border(2.dp, Color.White, CircleShape)
                .clickable(onClick = onChannelClick, role = Role.Button),
        )
        ShortAction(R.drawable.ic_share, stringResource(R.string.shorts_share), onClick = onShareClick)
        ShortAction(
            icon = R.drawable.ic_comment,
            label = commentCount?.let(::formatCompactCount) ?: stringResource(R.string.shorts_comments),
            onClick = onCommentsClick,
        )
        ShortAction(
            icon = R.drawable.ic_like,
            label = likes?.let { formatCompactCount(it.likeCount) } ?: stringResource(R.string.shorts_like),
            tint = likeTint,
            // Tant que le détail n'est pas chargé, l'état « j'aime » est inconnu : pas d'action.
            enabled = likes != null && !likes.isPending,
            onClick = onLikeClick,
        )
    }
}

@Composable
private fun ShortAction(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
    enabled: Boolean = true,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onClick, role = Role.Button),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(48.dp)
                .background(Color.Black.copy(alpha = 0.25f), CircleShape)
                .padding(12.dp),
        )
        Text(
            text = label,
            style = OverlayTextStyle.merge(MaterialTheme.typography.labelMedium),
            color = Color.White,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

// Ombre portée légère sous le texte posé sur la vidéo, en plus du dégradé.
private val OverlayTextStyle = TextStyle(shadow = Shadow(color = Color.Black.copy(alpha = 0.6f), blurRadius = 6f))

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

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 360, heightDp = 720)
@Composable
private fun ShortPagePreview() {
    YoutubeCloneTheme {
        ShortPage(
            video = previewVideo("1", "Le ballet des méduses, filmé de très près dans un aquarium géant", "Studio Libre", 2_300, 7),
            player = null,
            pausedByUser = true,
            blockedReason = null,
            premiumRequired = false,
            likes = ShortLikes(likeCount = 1_240, isLiked = true),
            commentCount = 36,
            followState = FollowUiState(followerCount = 15_800, isFollowing = false),
            onTap = {},
            onLikeClick = {},
            onCommentsClick = {},
            onShareClick = {},
            onChannelClick = {},
            onFollowClick = {},
            onPremiumClick = {},
        )
    }
}
