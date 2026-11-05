package com.youtubeclone.app.presentation.watch

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.view.OrientationEventListener
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
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
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.Channel
import com.youtubeclone.app.domain.model.Comment
import com.youtubeclone.app.domain.model.CommentAuthor
import com.youtubeclone.app.domain.model.DownloadState
import com.youtubeclone.app.domain.model.DownloadedVideo
import com.youtubeclone.app.domain.model.LikeStatus
import com.youtubeclone.app.domain.model.NotificationLevel
import com.youtubeclone.app.domain.model.Video
import com.youtubeclone.app.domain.model.VideoDetail
import com.youtubeclone.app.presentation.ads.AdPlaybackOverlay
import com.youtubeclone.app.presentation.ads.AdPlaybackState
import com.youtubeclone.app.presentation.channel.FollowButton
import com.youtubeclone.app.presentation.channel.FollowUiState
import com.youtubeclone.app.presentation.channel.NotificationPermissionRequest
import com.youtubeclone.app.presentation.common.BackTopBar
import com.youtubeclone.app.presentation.common.ChannelAvatar
import com.youtubeclone.app.presentation.common.ErrorState
import com.youtubeclone.app.presentation.common.ErrorToast
import com.youtubeclone.app.presentation.common.LoadingState
import com.youtubeclone.app.presentation.common.formatCompactCount
import com.youtubeclone.app.presentation.common.formatFollowerCount
import com.youtubeclone.app.presentation.common.formatRelativeDate
import com.youtubeclone.app.presentation.common.formatViewCount
import com.youtubeclone.app.presentation.common.MinTouchTarget
import com.youtubeclone.app.presentation.common.minTouchTarget
import com.youtubeclone.app.presentation.common.pillBackground
import com.youtubeclone.app.presentation.home.VideoCard
import com.youtubeclone.app.presentation.home.previewVideo
import com.youtubeclone.app.presentation.messaging.rememberVideoSharing
import com.youtubeclone.app.presentation.playlist.SaveToPlaylistSheet
import com.youtubeclone.app.presentation.theme.LocalReduceMotion
import com.youtubeclone.app.presentation.theme.Motion
import com.youtubeclone.app.presentation.theme.YoutubeCloneTheme
import java.time.Instant
import kotlin.math.abs

// Écran de lecture d'après les maquettes (android/maquettes/lecture-01, -36 et -54, captures iOS) :
// lecteur collé en haut, titre, actions, chaîne, aperçu des commentaires, puis vidéos suggérées.
// On reprend la mise en page et la hiérarchie, avec les conventions Android et Material.
//
// ADR-024 : l'écran de lecture est une couche posée au-dessus de la navigation, qui se réduit en
// mini-lecteur. Une seule vue vidéo (PlayerSurface), toujours au même endroit de l'arbre Compose,
// ne fait que changer de taille et de position : sa surface n'est jamais recréée.

private val ActionChipHeight = 36.dp
private val PillShape = RoundedCornerShape(50)

/** Hauteur du mini-lecteur ; la navigation laisse cette place en bas des écrans. */
val MiniPlayerHeight = 64.dp
private val MiniPlayerVideoWidth = MiniPlayerHeight * 16 / 9

// Distance d'un glissement pour réduire l'écran de lecture ou fermer le mini-lecteur.
private val SwipeThreshold = 80.dp

// Distance qui met la vue vidéo hors de l'écran, sans la détruire, quand le mini-lecteur est masqué.
private val OffscreenOffset = 4000.dp

/**
 * Couche du lecteur en cours : écran de lecture déployé, ou mini-lecteur. Ne dessine rien tant
 * qu'aucune vidéo n'est ouverte. [miniVisible] : le mini-lecteur peut s'afficher (faux sur les
 * écrans plein espace et quand le clavier est ouvert). Ouvrir un autre écran depuis l'écran de
 * lecture le réduit d'abord, pour que cet écran soit visible.
 */
@Composable
fun NowPlayingLayer(
    miniVisible: Boolean,
    onChannelClick: (channelId: String) -> Unit,
    onLoginRequired: () -> Unit,
    onPremiumRequired: (videoId: String) -> Unit,
    onRemoveAdsClick: () -> Unit,
    onOpenDownloads: () -> Unit,
    onShareToSomeone: (videoId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WatchViewModel = hiltViewModel(),
) {
    // Application masquée (ADR-024) : la libération du décodeur dépend de l'activité, plein lecteur
    // comme mini-lecteur. Un compte Premium garde le son (lecture en arrière-plan).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onAppStopped() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onAppStarted() }

    val videoId by viewModel.currentVideoId.collectAsStateWithLifecycle()
    val id = videoId ?: return
    val expanded by viewModel.expanded.collectAsStateWithLifecycle()

    // Retour réduit en mini-lecteur ; ignoré pendant une annonce (ADR-021). Déclaré avant le plein
    // écran, dont le Retour (sortie du plein écran) doit passer en premier.
    BackHandler(enabled = expanded) { viewModel.minimize() }
    val fullscreen = rememberFullscreen(enabled = expanded)

    // Ouvrir un autre écran depuis l'écran de lecture : il se réduit d'abord.
    fun leaving(action: () -> Unit) {
        viewModel.minimizeForNavigation()
        action()
    }

    val download by viewModel.download.collectAsStateWithLifecycle()
    // Sans Premium, Télécharger ouvre l'offre (ADR-019, décision 15).
    LaunchedEffect(viewModel) { viewModel.downloadNeedsPremium.collect { leaving(onRemoveAdsClick) } }
    // Android 13 et plus : la notification de progression des téléchargements demande une
    // autorisation. Refusée, le téléchargement se fait quand même, sans notification visible.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.startDownload()
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val adState by viewModel.ads.state.collectAsStateWithLifecycle()
    val showThumbnail by viewModel.showThumbnail.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val followState by viewModel.follow.state.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val commentsController by viewModel.comments.collectAsStateWithLifecycle()
    val commentsState by commentsController.state.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val autoplay by viewModel.autoplay.collectAsStateWithLifecycle()
    val qualities by viewModel.qualities.collectAsStateWithLifecycle()
    val selectedQuality by viewModel.selectedQuality.collectAsStateWithLifecycle()
    val autoplayEnabled by viewModel.autoplayEnabled.collectAsStateWithLifecycle()
    val subtitlesEnabled by viewModel.subtitlesEnabled.collectAsStateWithLifecycle()
    var showPlayerMenu by rememberSaveable { mutableStateOf(false) }
    var showSaveSheet by rememberSaveable { mutableStateOf(false) }
    var showComments by rememberSaveable { mutableStateOf(false) }
    // Défilement conservé pendant la réduction, remis en haut pour une autre vidéo.
    val listState = remember(id) { LazyListState() }

    // Fin du décompte de lecture automatique : la vidéo suivante s'ouvre comme une suggestion.
    LaunchedEffect(viewModel) {
        viewModel.autoplayNext.collect(viewModel::open)
    }
    ErrorToast(viewModel.follow.errors)
    NotificationPermissionRequest(viewModel.follow.bellActivated)
    ErrorToast(viewModel.errors)
    ErrorToast(commentsController.errors)

    // Connexion temps réel ouverte tant que l'écran de lecture est déployé et visible : fermée à la
    // réduction (commentaires et compteurs ne s'affichent que dans l'écran de lecture), rouverte
    // au redéploiement.
    val lifecycleOwner = LocalLifecycleOwner.current
    if (expanded) {
        LaunchedEffect(viewModel, lifecycleOwner, id) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.collectLiveUpdates()
            }
        }
    }

    // Refus du serveur : la vidéo est fermée et le paywall prend sa place.
    if (uiState is WatchUiState.PremiumRequired) {
        LaunchedEffect(id) {
            viewModel.close()
            onPremiumRequired(id)
        }
    }

    val context = LocalContext.current
    // Partager : « Envoyer à » pour un compte connecté (ADR-025, étape 2).
    val sharing = rememberVideoSharing(isLoggedIn) { videoId -> leaving { onShareToSomeone(videoId) } }

    // Visiteur : la connexion est demandée au moment d'agir.
    fun requireLogin(action: () -> Unit) = if (isLoggedIn) action() else leaving(onLoginRequired)

    val detail = (uiState as? WatchUiState.Success)?.detail

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val fullWidth = maxWidth
        val fullHeight = maxHeight
        if (expanded) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                when (val state = uiState) {
                    WatchUiState.Loading, WatchUiState.PremiumRequired -> Column(Modifier.fillMaxSize()) {
                        Spacer(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                        LoadingState(Modifier.weight(1f))
                    }
                    is WatchUiState.Error -> Column(Modifier.fillMaxSize()) {
                        Spacer(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                        ErrorState(state.error, viewModel::load, Modifier.weight(1f))
                    }
                    is WatchUiState.Success -> {
                        val video = state.detail.video
                        WatchContent(
                            fullscreen = fullscreen.isOn,
                            detail = state.detail,
                            stats = stats,
                            followState = followState,
                            comments = commentsState,
                            suggestions = suggestions,
                            listState = listState,
                            // La vue vidéo est à la racine de la couche : ici, seulement sa place.
                            player = { Spacer(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) },
                            onChannelClick = { leaving { onChannelClick(video.channel.id) } },
                            onFollowClick = { requireLogin(viewModel.follow::toggle) },
                            onNotificationLevelSelect = viewModel.follow::selectNotificationLevel,
                            onLikeClick = { requireLogin(viewModel::toggleLike) },
                            onDislikeClick = { requireLogin(viewModel::toggleDislike) },
                            onSaveClick = { requireLogin { showSaveSheet = true } },
                            download = download,
                            onDownloadClick = {
                                requireLogin {
                                    when {
                                        download != null -> leaving(onOpenDownloads)
                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                                            PackageManager.PERMISSION_GRANTED ->
                                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        else -> viewModel.startDownload()
                                    }
                                }
                            },
                            onShareClick = { sharing.share(video) },
                            onCommentsClick = { showComments = true },
                            onSuggestionClick = viewModel::open,
                        )
                    }
                }
            }
            if (showSaveSheet) {
                SaveToPlaylistSheet(videoId = id, onDismiss = { showSaveSheet = false })
            }
            if (showPlayerMenu) {
                PlayerMenuSheet(
                    qualities = qualities.map { it.size },
                    selectedQuality = selectedQuality,
                    onQualitySelected = {
                        viewModel.selectQuality(it)
                        showPlayerMenu = false
                    },
                    hasSubtitles = detail?.subtitleUrl != null,
                    subtitlesEnabled = subtitlesEnabled,
                    onSubtitlesChange = viewModel::setSubtitlesEnabled,
                    autoplayEnabled = autoplayEnabled,
                    onAutoplayChange = viewModel::onAutoplayChange,
                    onDismiss = { showPlayerMenu = false },
                )
            }
            if (showComments && detail != null) {
                CommentsSheet(
                    state = commentsState,
                    controller = commentsController,
                    isLoggedIn = isLoggedIn,
                    onLoginRequired = { leaving(onLoginRequired) },
                    onDismiss = { showComments = false },
                )
            }
        } else if (miniVisible) {
            // Masqué sur les écrans plein espace et sous le clavier : sa barre n'est pas dessinée,
            // pas seulement la vue vidéo envoyée hors écran (ADR-024).
            MiniPlayerBar(
                detail = detail,
                isPlaying = isPlaying,
                // Une annonce en pause attend le plein lecteur : lecture/pause le redéploie.
                onTogglePlay = { if (adState != null) viewModel.expand() else viewModel.togglePlayback() },
                onExpand = viewModel::expand,
                onClose = viewModel::close,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }

        // La seule vue vidéo : au-dessus de l'écran de lecture ou du mini-lecteur, qui réservent sa
        // place. Mini-lecteur masqué : hors de l'écran, avec la même taille, pour que sa surface
        // ne soit pas recréée au retour.
        val playerModifier = when {
            expanded && fullscreen.isOn -> Modifier.size(fullWidth, fullHeight)
            expanded -> Modifier.size(fullWidth, fullWidth * 9 / 16)
            miniVisible -> Modifier.offset(y = fullHeight - MiniPlayerHeight).size(MiniPlayerVideoWidth, MiniPlayerHeight)
            else -> Modifier.offset(y = fullHeight + OffscreenOffset).size(MiniPlayerVideoWidth, MiniPlayerHeight)
        }
        PlayerSurface(
            compact = !expanded,
            fullscreen = fullscreen.isOn,
            onFullscreenToggle = fullscreen::set,
            detail = detail,
            player = viewModel.player,
            showThumbnail = showThumbnail,
            adState = adState,
            adPlayer = viewModel.ads.player,
            onSkipAd = viewModel.ads::skip,
            onVisitAdSite = viewModel.ads::onVisitSite,
            onRemoveAdsClick = {
                viewModel.ads.onRemoveAdsClicked()
                leaving(onRemoveAdsClick)
            },
            autoplay = autoplay,
            onCancelAutoplay = viewModel::cancelAutoplay,
            onBack = { viewModel.minimize() },
            onExpand = viewModel::expand,
            onClose = viewModel::close,
            onMenuClick = { showPlayerMenu = true },
            subtitlesEnabled = subtitlesEnabled,
            onSubtitlesClick = { viewModel.setSubtitlesEnabled(!subtitlesEnabled) },
            modifier = playerModifier,
        )
    }
}

// Mini-lecteur ancré en bas : la vidéo en petit à gauche (la vue vidéo flotte à cet emplacement),
// puis titre, chaîne, lecture/pause et croix. Un appui rouvre l'écran de lecture, un glissement
// latéral ferme.
@Composable
private fun MiniPlayerBar(
    detail: VideoDetail?,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val expandLabel = stringResource(R.string.a11y_mini_player_expand)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .swipeToDismiss(onSwipeSideways = onClose),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(MiniPlayerVideoWidth).fillMaxHeight())
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(onClickLabel = expandLabel, role = Role.Button, onClick = onExpand)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = detail?.video?.title.orEmpty(),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail?.video?.channel?.name.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onTogglePlay, enabled = detail != null) {
            Icon(
                painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = stringResource(if (isPlaying) R.string.mini_player_pause else R.string.mini_player_play),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        IconButton(onClick = onClose) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.mini_player_close),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

/**
 * Détecte un glissement vers le bas ([onSwipeDown]) ou sur le côté ([onSwipeSideways]) sans rien
 * consommer : les contrôles de Media3 en dessous gardent leurs appuis et leurs glissements. Le
 * geste est jugé au relâchement, s'il dépasse [SwipeThreshold] et reste net dans son axe.
 */
private fun Modifier.swipeToDismiss(
    onSwipeDown: (() -> Unit)? = null,
    onSwipeSideways: (() -> Unit)? = null,
): Modifier = composed {
    val thresholdPx = with(LocalDensity.current) { SwipeThreshold.toPx() }
    val currentDown by rememberUpdatedState(onSwipeDown)
    val currentSideways by rememberUpdatedState(onSwipeSideways)
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var dx = 0f
            var dy = 0f
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (!change.pressed) break
                dx += change.positionChange().x
                dy += change.positionChange().y
            }
            when {
                dy > thresholdPx && dy > 2 * abs(dx) -> currentDown?.invoke()
                abs(dx) > thresholdPx && abs(dx) > 2 * abs(dy) -> currentSideways?.invoke()
            }
        }
    }
}

@Composable
private fun WatchContent(
    fullscreen: Boolean,
    detail: VideoDetail,
    stats: LiveStats?,
    followState: FollowUiState?,
    comments: CommentsUiState,
    suggestions: List<Video>,
    listState: LazyListState,
    player: @Composable () -> Unit,
    onChannelClick: () -> Unit,
    onFollowClick: () -> Unit,
    onNotificationLevelSelect: (NotificationLevel) -> Unit,
    onLikeClick: () -> Unit,
    onDislikeClick: () -> Unit,
    onSaveClick: () -> Unit,
    onShareClick: () -> Unit,
    download: DownloadedVideo?,
    onDownloadClick: () -> Unit,
    onCommentsClick: () -> Unit,
    onSuggestionClick: (videoId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Place du lecteur, collée en haut, toujours visible pendant le défilement. En plein écran,
        // il est seul : sa vue (à la racine de la couche) n'est pas recréée, la lecture continue.
        player()
        if (!fullscreen) LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "title") { TitleBlock(detail, viewCount = stats?.viewCount ?: detail.video.viewCount) }
            item(key = "channel") { ChannelRow(detail.video.channel, followState, onChannelClick, onFollowClick, onNotificationLevelSelect) }
            item(key = "actions") { ActionsRow(
                    stats = stats,
                    onLikeClick = onLikeClick,
                    onDislikeClick = onDislikeClick,
                    onShareClick = onShareClick,
                    onSaveClick = onSaveClick,
                    // Vidéo externe : pas téléchargeable, pas de bouton (décision 8).
                    canDownload = detail.videoUrl?.contains("/media/videos/") == true,
                    download = download,
                    onDownloadClick = onDownloadClick,
                ) }
            // Commentaires désactivés (ADR-016) : le bloc n'apparaît pas.
            if (detail.video.commentsEnabled) {
                item(key = "comments") { CommentsTeaser(comments, onCommentsClick) }
            }
            items(suggestions, key = { it.id }) { video ->
                VideoCard(
                    video = video,
                    modifier = Modifier.clickable { onSuggestionClick(video.id) },
                )
            }
        }
    }
}

// Vue vidéo unique (ADR-024) : plein lecteur en haut de l'écran de lecture, plein écran, ou vignette
// du mini-lecteur ([compact]). Elle porte aussi les boutons et surcouches du lecteur. [detail] est
// null pendant le chargement : la vue reste en place, noire, pour ne pas être recréée.
@Composable
private fun PlayerSurface(
    compact: Boolean,
    fullscreen: Boolean,
    onFullscreenToggle: (Boolean) -> Unit,
    detail: VideoDetail?,
    player: Player,
    showThumbnail: Boolean,
    adState: AdPlaybackState?,
    adPlayer: Player,
    onSkipAd: () -> Unit,
    onVisitAdSite: () -> Unit,
    onRemoveAdsClick: () -> Unit,
    autoplay: AutoplayCountdown?,
    onCancelAutoplay: () -> Unit,
    onBack: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    onMenuClick: () -> Unit,
    subtitlesEnabled: Boolean,
    onSubtitlesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Surcouche de nos boutons : en plein écran, elle suit la barre de contrôle de Media3, affichée
    // d'un appui et masquée au second ou après 3 secondes ; en portrait, toujours visible. Aucune
    // en mini-lecteur.
    var controlsVisible by remember { mutableStateOf(false) }
    val showOverlay = !compact && (!fullscreen || controlsVisible)
    Box(
        // Glissement vers le bas : sort du plein écran, sinon réduit (jamais pendant une annonce,
        // ADR-021). En mini-lecteur, un glissement latéral ferme.
        modifier = modifier
            .background(Color.Black)
            .swipeToDismiss(
                onSwipeDown = if (!compact && adState == null) {
                    { if (fullscreen) onFullscreenToggle(false) else onBack() }
                } else {
                    null
                },
                onSwipeSideways = if (compact) onClose else null,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (detail == null || detail.videoUrl != null) {
            VideoPlayer(
                player = player,
                fullscreen = fullscreen,
                compact = compact,
                onFullscreenToggle = onFullscreenToggle,
                onControlsVisibilityChange = { controlsVisible = it },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (detail != null && detail.videoUrl != null) {
            // Miniature par-dessus le lecteur tant que la première image n'est pas affichée : au
            // lancement, et au retour d'une autre application, pendant que la vidéo se recharge.
            // Un écran noir ferait croire à un plantage.
            if (showThumbnail) {
                AsyncImage(
                    model = detail.video.thumbnailUrl,
                    // Décoratif (ADR-030) : le sens est porté par le texte voisin, TalkBack ignore l'image.
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Publicité avant la vidéo (ADR-021), en plein lecteur et sans contrôle de lecture.
            if (adState != null && !compact) {
                AdPlaybackOverlay(adState, adPlayer, onSkipAd, onRemoveAdsClick, onVisitAdSite, Modifier.fillMaxSize())
            }
            if (autoplay != null && !compact) {
                AutoplayOverlay(autoplay, onCancelAutoplay, Modifier.fillMaxSize())
            }
            // Pendant une annonce, ni flèche ni glissement : elle ne se réduit jamais (ADR-021).
            if (showOverlay && adState == null) {
                // En plein écran, la flèche sort du plein écran avant de réduire, comme Retour.
                BackTopBar(
                    title = "",
                    onBack = if (fullscreen) ({ onFullscreenToggle(false) }) else onBack,
                    contentColor = Color.White,
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
            // Sous-titres et menu du lecteur : pas pendant la publicité.
            if (adState == null && showOverlay) Row(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                // Sous-titres générés automatiquement (ADR-017), seulement si la vidéo en a.
                if (detail.subtitleUrl != null) {
                    IconButton(onClick = onSubtitlesClick) {
                        Icon(
                            painter = painterResource(
                                if (subtitlesEnabled) R.drawable.ic_subtitles_on else R.drawable.ic_subtitles_off,
                            ),
                            contentDescription = stringResource(
                                if (subtitlesEnabled) R.string.player_subtitles_disable else R.string.player_subtitles_enable,
                            ),
                            tint = Color.White,
                        )
                    }
                }
                // Menu du lecteur : qualité, sous-titres et lecture automatique (ADR-017).
                IconButton(onClick = onMenuClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings),
                        contentDescription = stringResource(R.string.player_menu),
                        tint = Color.White,
                    )
                }
            }
        } else if (detail != null && !compact) {
            // Le serveur n'a pas donné l'URL : accès refusé, par exemple vidéo Premium.
            Text(
                text = stringResource(R.string.watch_premium_only),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
        }
        // Mini-lecteur : un appui sur la vignette rouvre l'écran de lecture.
        if (compact) {
            // Même action que le titre du mini-lecteur, déjà annoncé : cette zone est cachée à TalkBack.
            Box(Modifier.fillMaxSize().clickable(onClick = onExpand).semantics { hideFromAccessibility() })
        }
    }
}

// Fin de la vidéo, lecture automatique activée : décompte avant la première suggestion, avec
// son titre et un bouton Annuler. Par-dessus le lecteur, qui reste sur sa dernière image.
@Composable
private fun AutoplayOverlay(countdown: AutoplayCountdown, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(R.string.autoplay_next_in, countdown.secondsLeft),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.8f),
        )
        Text(
            text = countdown.next.title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.autoplay_cancel),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onCancel, role = Role.Button)
                .background(Color.White.copy(alpha = 0.15f))
                .padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

// Vue native de Media3, avec ses contrôles intégrés.
@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(
    player: Player,
    fullscreen: Boolean,
    compact: Boolean,
    onFullscreenToggle: (Boolean) -> Unit,
    onControlsVisibilityChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentFullscreen by rememberUpdatedState(fullscreen)
    val currentToggle by rememberUpdatedState(onFullscreenToggle)
    val currentVisibility by rememberUpdatedState(onControlsVisibilityChange)
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                this.player = player
                // Bouton plein écran de Media3, en bas à droite de la barre, comme sur YouTube.
                // setFullscreenButtonState (dans update) appelle aussi cet écouteur : un appel qui
                // demande l'état actuel vient de là, pas d'un appui, et verrouillerait l'orientation.
                setFullscreenButtonClickListener { enter -> if (enter != currentFullscreen) currentToggle(enter) }
                // Barre masquée après 3 secondes sans action ; nos boutons la suivent en plein écran.
                controllerShowTimeoutMs = CONTROLS_TIMEOUT_MS
                setControllerVisibilityListener(
                    PlayerView.ControllerVisibilityListener { visibility -> currentVisibility(visibility == View.VISIBLE) },
                )
                // Indicateur de chargement pendant la mise en mémoire tampon.
                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                // Roue dentée de Media3 masquée : notre menu (qualité, sous-titres, lecture
                // automatique) est le seul. Media3 l'active ou la grise, sans jamais la réafficher.
                findViewById<View>(androidx.media3.ui.R.id.exo_settings)?.visibility = View.GONE
            }
        },
        update = { view ->
            view.setFullscreenButtonState(fullscreen)
            // Mini-lecteur : pas de barre de contrôle, la vue ne fait qu'afficher l'image.
            view.useController = !compact
        },
        // La vue est détachée du lecteur à la fermeture ; le lecteur, lui, reste au ViewModel.
        onRelease = { view -> view.player = null },
        modifier = modifier,
    )
}

private const val CONTROLS_TIMEOUT_MS = 3_000

/** Plein écran de l'écran de lecture : [isOn] en paysage ; [set] y entre ou en sort. */
private class FullscreenState(val isOn: Boolean, private val onSet: (Boolean) -> Unit) {
    fun set(on: Boolean) = onSet(on)
}

/**
 * Plein écran = paysage, par le bouton du lecteur (chemin principal) ou en tournant le téléphone
 * (raccourci). Le bouton de sortie et Retour ramènent en portrait, verrouillé jusqu'à ce que le
 * téléphone soit de nouveau tenu droit : sinon, tenu à l'horizontale, il repasserait aussitôt en
 * paysage. De même, le bouton d'entrée verrouille le paysage jusqu'à ce que le téléphone soit
 * tourné de côté. Barres du système masquées en plein écran ; tout est rétabli en quittant l'écran.
 * [enabled] faux (mini-lecteur) : jamais de plein écran, quelle que soit l'orientation.
 */
@Composable
private fun rememberFullscreen(enabled: Boolean): FullscreenState {
    val activity = LocalActivity.current
    val isOn = enabled && LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val set: (Boolean) -> Unit = { on ->
        activity?.requestedOrientation =
            if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    BackHandler(enabled = isOn) { set(false) }
    if (activity != null && enabled) {
        LaunchedEffect(isOn) {
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            if (isOn) {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        DisposableEffect(activity) {
            // Verrou posé par le bouton levé dès que le téléphone est tenu dans le même sens : droit
            // pour le portrait, de côté pour le paysage. La rotation redevient alors un raccourci,
            // dans les deux sens : redresser le téléphone sort du plein écran.
            val listener = object : OrientationEventListener(activity) {
                override fun onOrientationChanged(degrees: Int) {
                    if (degrees == ORIENTATION_UNKNOWN) return
                    val upright = degrees < 30 || degrees > 330
                    val sideways = degrees in 60..120 || degrees in 240..300
                    val locked = activity.requestedOrientation
                    if ((upright && locked == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) ||
                        (sideways && locked == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
                    ) {
                        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
            }
            listener.enable()
            onDispose {
                listener.disable()
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    return FullscreenState(isOn, set)
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
            // Police très agrandie (ADR-030) : le titre s'affiche en entier, sans « … ».
            maxLines = if (LocalDensity.current.fontScale > 1.3f) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
            // Titre de la vidéo : annoncé comme un titre (ADR-030).
            modifier = Modifier.semantics { heading() },
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
                        .clickable(role = Role.Button) { expanded = !expanded }
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
    onNotificationLevelSelect: (NotificationLevel) -> Unit,
) {
    // Police très agrandie (ADR-030) : le nombre d'abonnés passe sous le nom, et le bouton S'abonner
    // sous la ligne, pour que le nom reste lisible en entier au lieu d'être réduit à « Be… ».
    // Police normale : mise en page inchangée.
    val stacked = LocalDensity.current.fontScale > 1.3f
    val info: @Composable (Modifier) -> Unit = { infoModifier ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = infoModifier
                .minTouchTarget()
                .clickable(role = Role.Button, onClick = onChannelClick),
        ) {
            ChannelAvatar(channel, Modifier.size(32.dp))
            val nameText: @Composable (Modifier) -> Unit = { nameModifier ->
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = if (stacked) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = nameModifier,
                )
            }
            val countText: @Composable () -> Unit = {
                if (followState != null) {
                    Text(
                        text = formatFollowerCount(followState.followerCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            if (stacked) {
                Column(Modifier.weight(1f)) {
                    nameText(Modifier)
                    countText()
                }
            } else {
                nameText(Modifier.weight(1f, fill = false))
                countText()
            }
        }
    }
    val follow: @Composable () -> Unit = {
        if (followState != null && followState.canFollow) {
            FollowButton(state = followState, onClick = onFollowClick, onNotificationLevelSelect = onNotificationLevelSelect)
        }
    }
    if (stacked) {
        Column(modifier = Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            info(Modifier.fillMaxWidth())
            follow()
        }
    } else {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            info(Modifier.weight(1f))
            follow()
        }
    }
}

// Actions défilantes : J'aime / Je n'aime pas, Partager, Télécharger, Enregistrer.
@Composable
private fun ActionsRow(
    stats: LiveStats?,
    onLikeClick: () -> Unit,
    onDislikeClick: () -> Unit,
    onShareClick: () -> Unit,
    onSaveClick: () -> Unit,
    canDownload: Boolean,
    download: DownloadedVideo?,
    onDownloadClick: () -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (stats != null) {
            item(key = "like") { LikeDislikeChip(stats, onLikeClick, onDislikeClick) }
        }
        item(key = "share") { ActionChip(R.drawable.ic_share, stringResource(R.string.watch_share), onShareClick) }
        if (canDownload) {
            item(key = "download") {
                val label = when (download?.state) {
                    null -> stringResource(R.string.watch_download)
                    DownloadState.Completed -> stringResource(R.string.watch_downloaded)
                    DownloadState.Failed -> stringResource(R.string.downloads_failed)
                    else -> stringResource(R.string.downloads_in_progress, (download.progress * 100).toInt())
                }
                ActionChip(R.drawable.ic_download, label, onDownloadClick)
            }
        }
        item(key = "save") { ActionChip(R.drawable.ic_save, stringResource(R.string.watch_save), onSaveClick) }
    }
}

@Composable
private fun ActionChip(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    // Zone cliquable de 48 dp (ADR-030) ; la pastille dessinée garde ses 36 dp, centrée.
    Row(
        modifier = Modifier
            .height(MinTouchTarget)
            .pillBackground(MaterialTheme.colorScheme.surfaceVariant, ActionChipHeight)
            .clip(PillShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Décoratif (ADR-030) : le sens est porté par le texte voisin, TalkBack ignore l'image.
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
    }
}

// Une seule pastille, séparée par un trait : J'aime avec son compteur, puis Je n'aime pas, sans
// compteur (ADR-019). Le bouton actif prend la couleur d'accent.
@Composable
private fun LikeDislikeChip(stats: LiveStats, onLikeClick: () -> Unit, onDislikeClick: () -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    val stateText = stringResource(if (stats.isLiked) R.string.watch_liked else R.string.watch_not_liked)
    val dislikeStateText = stringResource(if (stats.isDisliked) R.string.watch_disliked else R.string.watch_not_disliked)
    val dislikeTint by animateColorAsState(
        targetValue = if (stats.isDisliked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
        animationSpec = tween(if (reduceMotion) Motion.REDUCED_FADE_MS else Motion.SHORT_MS),
        label = "dislikeTint",
    )

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

    // Zones cliquables de 48 dp (ADR-030) ; la pastille dessinée garde ses 36 dp, centrée.
    Row(
        modifier = Modifier
            .height(MinTouchTarget)
            .pillBackground(MaterialTheme.colorScheme.surfaceVariant, ActionChipHeight)
            .clip(PillShape),
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
                .clickable(enabled = !stats.likePending, onClick = onDislikeClick)
                .semantics {
                    role = Role.Switch
                    stateDescription = dislikeStateText
                }
                .padding(start = 12.dp, end = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_dislike),
                contentDescription = stringResource(R.string.watch_dislike),
                tint = dislikeTint,
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
            .clickable(role = Role.Button, onClick = onClick)
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

@Preview(showBackground = true, backgroundColor = 0xFF0F0F0F, heightDp = 900)
@Composable
private fun WatchContentPreview() {
    YoutubeCloneTheme {
        val video = previewVideo("1", "Sintel, la bande-annonce du court métrage d'animation libre de la fondation Blender", "Studio Libre", 12_400, 3)
        WatchContent(
            fullscreen = false,
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
            listState = rememberLazyListState(),
            player = { Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) },
            onChannelClick = {},
            onFollowClick = {},
            onNotificationLevelSelect = {},
            onLikeClick = {},
            onDislikeClick = {},
            onSaveClick = {},
            download = null,
            onDownloadClick = {},
            onShareClick = {},
            onCommentsClick = {},
            onSuggestionClick = {},
        )
    }
}
