package com.youtubeclone.app.presentation.watch

import android.content.Intent
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.Channel
import com.youtubeclone.app.domain.model.Comment
import com.youtubeclone.app.domain.model.CommentAuthor
import com.youtubeclone.app.domain.model.LikeStatus
import com.youtubeclone.app.domain.model.Video
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
import com.youtubeclone.app.presentation.home.previewVideo
import com.youtubeclone.app.presentation.theme.LocalReduceMotion
import com.youtubeclone.app.presentation.theme.Motion
import com.youtubeclone.app.presentation.theme.YoutubeCloneTheme
import java.time.Instant

// Écran de lecture d'après les maquettes (android/maquettes/lecture-01, -36 et -54, captures iOS) :
// lecteur collé en haut, titre, actions, chaîne, aperçu des commentaires, puis vidéos suggérées.
// On reprend la mise en page et la hiérarchie, avec les conventions Android et Material.

private val ActionChipHeight = 36.dp
private val PillShape = RoundedCornerShape(50)

@Composable
fun WatchScreen(
    onVideoClick: (videoId: String) -> Unit,
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
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
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

    val context = LocalContext.current
    val shareChooserTitle = stringResource(R.string.watch_share_chooser)
    var showComments by rememberSaveable { mutableStateOf(false) }

    // Visiteur : la connexion est demandée au moment d'agir.
    fun requireLogin(action: () -> Unit) = if (isLoggedIn) action() else onLoginRequired()

    when (val state = uiState) {
        WatchUiState.Loading -> LoadingState(modifier)
        is WatchUiState.Error -> ErrorState(state.error, viewModel::load, modifier)
        is WatchUiState.Success -> {
            val video = state.detail.video
            WatchContent(
                detail = state.detail,
                stats = stats,
                followState = followState,
                comments = commentsState,
                suggestions = suggestions,
                player = { PlayerArea(state.detail, viewModel.player) },
                onChannelClick = { onChannelClick(video.channel.id) },
                onFollowClick = { requireLogin(viewModel.follow::toggle) },
                onLikeClick = { requireLogin(viewModel::toggleLike) },
                // Il n'y a pas encore d'adresse web publique des vidéos : on partage le titre et la chaîne.
                onShareClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, video.title)
                        putExtra(Intent.EXTRA_TEXT, "${video.title} · ${video.channel.name}")
                    }
                    context.startActivity(Intent.createChooser(intent, shareChooserTitle))
                },
                // Je n'aime pas, Télécharger et Enregistrer : pas encore de fonctionnalité derrière
                // (pas d'API ; playlists hors périmètre). Les boutons suivent la maquette.
                onUnavailableClick = {
                    Toast.makeText(context, R.string.watch_soon, Toast.LENGTH_SHORT).show()
                },
                onCommentsClick = { showComments = true },
                onSuggestionClick = onVideoClick,
                modifier = modifier,
            )
            if (showComments) {
                CommentsSheet(onDismiss = { showComments = false }) {
                    CommentsSection(
                        state = commentsState,
                        isLoggedIn = isLoggedIn,
                        onDraftChange = viewModel.comments::onDraftChange,
                        onPost = viewModel.comments::post,
                        onLoginRequired = {
                            showComments = false
                            onLoginRequired()
                        },
                        onLoadMore = viewModel.comments::loadMore,
                        onRetry = viewModel.comments::loadFirstPage,
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchContent(
    detail: VideoDetail,
    stats: LiveStats?,
    followState: FollowUiState?,
    comments: CommentsUiState,
    suggestions: List<Video>,
    player: @Composable () -> Unit,
    onChannelClick: () -> Unit,
    onFollowClick: () -> Unit,
    onLikeClick: () -> Unit,
    onShareClick: () -> Unit,
    onUnavailableClick: () -> Unit,
    onCommentsClick: () -> Unit,
    onSuggestionClick: (videoId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Lecteur collé en haut, toujours visible pendant le défilement.
        player()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "title") { TitleBlock(detail, viewCount = stats?.viewCount ?: detail.video.viewCount) }
            item(key = "channel") { ChannelRow(detail.video.channel, followState, onChannelClick, onFollowClick) }
            item(key = "actions") { ActionsRow(stats, onLikeClick, onShareClick, onUnavailableClick) }
            item(key = "comments") { CommentsTeaser(comments, onCommentsClick) }
            items(suggestions, key = { it.id }) { video ->
                SuggestedVideoCard(
                    video = video,
                    modifier = Modifier.clickable { onSuggestionClick(video.id) },
                )
            }
        }
    }
}

@Composable
private fun PlayerArea(detail: VideoDetail, player: Player) {
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

// Titre sur deux lignes au plus, puis « vues · date » en gris. « …plus » déplie la description.
@Composable
private fun TitleBlock(detail: VideoDetail, viewCount: Int) {
    val video = detail.video
    var expanded by rememberSaveable(video.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = video.title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${formatViewCount(viewCount)} · ${formatRelativeDate(video.publishedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (detail.description != null) {
                Text(
                    text = stringResource(if (expanded) R.string.watch_less else R.string.watch_more),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        if (expanded && detail.description != null) {
            Text(
                text = detail.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
            )
        }
    }
}

// Avatar, nom et abonnés ouvrent la chaîne ; S'abonner à droite, une fois l'état connu.
@Composable
private fun ChannelRow(
    channel: Channel,
    followState: FollowUiState?,
    onChannelClick: () -> Unit,
    onFollowClick: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp),
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
            ChannelAvatar(channel, Modifier.size(32.dp))
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (followState != null) {
                Text(
                    text = formatFollowerCount(followState.followerCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        if (followState != null && followState.canFollow) {
            FollowButton(state = followState, onClick = onFollowClick)
        }
    }
}

// Actions défilantes : J'aime / Je n'aime pas, Partager, Télécharger, Enregistrer.
@Composable
private fun ActionsRow(
    stats: LiveStats?,
    onLikeClick: () -> Unit,
    onShareClick: () -> Unit,
    onUnavailableClick: () -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (stats != null) {
            item(key = "like") { LikeDislikeChip(stats, onLikeClick, onDislikeClick = onUnavailableClick) }
        }
        item(key = "share") { ActionChip(R.drawable.ic_share, stringResource(R.string.watch_share), onShareClick) }
        item(key = "download") { ActionChip(R.drawable.ic_download, stringResource(R.string.watch_download), onUnavailableClick) }
        item(key = "save") { ActionChip(R.drawable.ic_save, stringResource(R.string.watch_save), onUnavailableClick) }
    }
}

@Composable
private fun ActionChip(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(ActionChipHeight)
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
    }
}

// Une seule pastille, séparée par un trait : J'aime avec son compteur, puis Je n'aime pas.
@Composable
private fun LikeDislikeChip(stats: LiveStats, onLikeClick: () -> Unit, onDislikeClick: () -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    val stateText = stringResource(if (stats.isLiked) R.string.watch_liked else R.string.watch_not_liked)

    // Rebond bref de l'icône à chaque clic (supprimé avec les animations réduites).
    val iconScale = remember { Animatable(1f) }
    var clicks by remember { mutableIntStateOf(0) }
    LaunchedEffect(clicks) {
        if (clicks == 0 || reduceMotion) return@LaunchedEffect
        iconScale.animateTo(LIKE_BOUNCE_SCALE, tween(Motion.SHORT_MS / 2, easing = Motion.Easing))
        iconScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }
    val tint by animateColorAsState(
        targetValue = if (stats.isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
        animationSpec = tween(if (reduceMotion) Motion.REDUCED_FADE_MS else Motion.SHORT_MS),
        label = "likeTint",
    )

    Row(
        modifier = Modifier
            .height(ActionChipHeight)
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .clickable(enabled = !stats.likePending) {
                    clicks++
                    onLikeClick()
                }
                .semantics {
                    role = Role.Switch
                    stateDescription = stateText
                }
                .padding(start = 14.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_like),
                contentDescription = stringResource(R.string.watch_like),
                tint = tint,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        scaleX = iconScale.value
                        scaleY = iconScale.value
                    },
            )
            // Le compteur change avec un fondu vertical : il monte quand il augmente, descend sinon.
            AnimatedContent(
                targetState = stats.likeCount,
                transitionSpec = {
                    if (reduceMotion) {
                        fadeIn(tween(Motion.REDUCED_FADE_MS)) togetherWith fadeOut(tween(Motion.REDUCED_FADE_MS))
                    } else {
                        val direction = if (targetState > initialState) 1 else -1
                        val spec = tween<IntOffset>(Motion.FADE_MS, easing = Motion.Easing)
                        (slideInVertically(spec) { height -> height * direction } + fadeIn(tween(Motion.FADE_MS))) togetherWith
                            (slideOutVertically(spec) { height -> -height * direction } + fadeOut(tween(Motion.FADE_MS)))
                    }.using(SizeTransform(clip = false))
                },
                label = "likeCount",
            ) { count ->
                Text(formatCompactCount(count), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
            }
        }
        Box(
            Modifier
                .width(1.dp)
                .height(20.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)),
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .clickable(onClick = onDislikeClick)
                .padding(start = 12.dp, end = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_dislike),
                contentDescription = stringResource(R.string.watch_dislike),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private const val LIKE_BOUNCE_SCALE = 1.3f

// Bloc Commentaires replié : « Commentaires N » et le premier commentaire. Il ouvre la liste.
@Composable
private fun CommentsTeaser(state: CommentsUiState, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.comments_title),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (!state.isLoading && state.error == null) {
                Text(
                    text = formatCompactCount(state.total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val first = state.comments.firstOrNull()
        if (first != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AuthorAvatar(first.author, size = 24.dp)
                Text(
                    text = first.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else if (!state.isLoading) {
            Text(
                text = stringResource(R.string.comments_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// Liste complète des commentaires, dans une feuille du bas (Material).
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            content()
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F0F0F, heightDp = 900)
@Composable
private fun WatchContentPreview() {
    YoutubeCloneTheme {
        val video = previewVideo("1", "Sintel, la bande-annonce du court métrage d'animation libre de la fondation Blender", "Studio Libre", 12_400, 3)
        WatchContent(
            detail = VideoDetail(
                video = video,
                description = "Une jeune femme part à la recherche de son dragon.",
                videoUrl = null,
                likeStatus = LikeStatus(likeCount = 780, isLiked = false),
            ),
            stats = LiveStats(viewCount = 12_400, likeCount = 780, isLiked = false),
            followState = FollowUiState(followerCount = 15_800, isFollowing = false),
            comments = CommentsUiState(
                comments = listOf(
                    Comment(
                        id = "c1",
                        videoId = "1",
                        content = "Toujours aussi beau, des années après.",
                        createdAt = Instant.now(),
                        author = CommentAuthor(id = "a", displayName = "Camille", avatarUrl = null),
                    ),
                ),
                total = 316,
                isLoading = false,
            ),
            suggestions = listOf(
                previewVideo("2", "Le ballet des méduses", "Studio Libre", 2_300, 7),
                previewVideo("3", "Une demi-minute en ville", "Carnet de Route", 980, 12),
            ),
            player = { Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) },
            onChannelClick = {},
            onFollowClick = {},
            onLikeClick = {},
            onShareClick = {},
            onUnavailableClick = {},
            onCommentsClick = {},
            onSuggestionClick = {},
        )
    }
}
