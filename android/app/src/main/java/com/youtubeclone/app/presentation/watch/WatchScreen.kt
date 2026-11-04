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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
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
import com.youtubeclone.app.presentation.common.shareVideo
import com.youtubeclone.app.presentation.home.VideoCard
import com.youtubeclone.app.presentation.home.previewVideo
import com.youtubeclone.app.presentation.playlist.SaveToPlaylistSheet
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
    onBack: () -> Unit,
    onVideoClick: (videoId: String) -> Unit,
    onChannelClick: (channelId: String) -> Unit,
    onLoginRequired: () -> Unit,
    onPremiumRequired: (videoId: String) -> Unit,
    onRemoveAdsClick: () -> Unit,
    onOpenDownloads: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WatchViewModel = hiltViewModel(),
) {
    val download by viewModel.download.collectAsStateWithLifecycle()
    // Sans Premium, Télécharger ouvre l'offre (ADR-019, décision 15).
    LaunchedEffect(viewModel) { viewModel.downloadNeedsPremium.collect { onRemoveAdsClick() } }
    // Android 13 et plus : la notification de progression des téléchargements demande une
    // autorisation. Refusée, le téléchargement se fait quand même, sans notification visible.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.startDownload()
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val adState by viewModel.ads.state.collectAsStateWithLifecycle()
    val showThumbnail by viewModel.showThumbnail.collectAsStateWithLifecycle()
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val followState by viewModel.follow.state.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val commentsState by viewModel.comments.state.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val autoplay by viewModel.autoplay.collectAsStateWithLifecycle()
    val qualities by viewModel.qualities.collectAsStateWithLifecycle()
    val selectedQuality by viewModel.selectedQuality.collectAsStateWithLifecycle()
    val autoplayEnabled by viewModel.autoplayEnabled.collectAsStateWithLifecycle()
    val subtitlesEnabled by viewModel.subtitlesEnabled.collectAsStateWithLifecycle()
    var showPlayerMenu by rememberSaveable { mutableStateOf(false) }
    var showSaveSheet by rememberSaveable { mutableStateOf(false) }
    if (showSaveSheet) {
        SaveToPlaylistSheet(videoId = viewModel.videoId, onDismiss = { showSaveSheet = false })
    }
    if (showPlayerMenu) {
        PlayerMenuSheet(
            qualities = qualities.map { it.size },
            selectedQuality = selectedQuality,
            onQualitySelected = {
                viewModel.selectQuality(it)
                showPlayerMenu = false
            },
            hasSubtitles = (uiState as? WatchUiState.Success)?.detail?.subtitleUrl != null,
            subtitlesEnabled = subtitlesEnabled,
            onSubtitlesChange = viewModel::setSubtitlesEnabled,
            autoplayEnabled = autoplayEnabled,
            onAutoplayChange = viewModel::onAutoplayChange,
            onDismiss = { showPlayerMenu = false },
        )
    }
    // Fin du décompte de lecture automatique : la vidéo suivante s'ouvre comme une suggestion.
    LaunchedEffect(viewModel) {
        viewModel.autoplayNext.collect(onVideoClick)
    }
    ErrorToast(viewModel.follow.errors)
    NotificationPermissionRequest(viewModel.follow.bellActivated)
    ErrorToast(viewModel.errors)
    ErrorToast(viewModel.comments.errors)

    // Écran masqué. Si l'activité reste visible, c'est une autre destination qui le recouvre
    // (autre vidéo, chaîne…) : pause. Sinon l'application passe en arrière-plan : un compte
    // Premium garde le son (lecture en arrière-plan).
    val activity = LocalActivity.current as? LifecycleOwner
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        val appInBackground = activity?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) != true
        viewModel.onScreenStopped(appInBackground)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onScreenStarted() }

    // Connexion temps réel ouverte tant que l'écran est visible : fermée quand on le quitte
    // (autre écran, application en arrière-plan), rouverte au retour.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.collectLiveUpdates()
        }
    }

    val context = LocalContext.current
    var showComments by rememberSaveable { mutableStateOf(false) }
    val fullscreen = rememberFullscreen()

    // Visiteur : la connexion est demandée au moment d'agir.
    fun requireLogin(action: () -> Unit) = if (isLoggedIn) action() else onLoginRequired()

    when (val state = uiState) {
        WatchUiState.Loading -> LoadingState(modifier)
        is WatchUiState.Error -> ErrorState(state.error, viewModel::load, modifier)
        // Refus du serveur : l'écran laisse sa place au paywall.
        WatchUiState.PremiumRequired -> {
            LoadingState(modifier)
            LaunchedEffect(Unit) { onPremiumRequired(viewModel.videoId) }
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
                player = {
                    PlayerArea(
                        fullscreen = fullscreen.isOn,
                        onFullscreenToggle = fullscreen::set,
                        detail = state.detail,
                        player = viewModel.player,
                        showThumbnail = showThumbnail,
                        adState = adState,
                        adPlayer = viewModel.ads.player,
                        onSkipAd = viewModel.ads::skip,
                        onVisitAdSite = viewModel.ads::onVisitSite,
                        onRemoveAdsClick = {
                            viewModel.ads.onRemoveAdsClicked()
                            onRemoveAdsClick()
                        },
                        autoplay = autoplay,
                        onCancelAutoplay = viewModel::cancelAutoplay,
                        onBack = onBack,
                        onMenuClick = { showPlayerMenu = true },
                        subtitlesEnabled = subtitlesEnabled,
                        onSubtitlesClick = { viewModel.setSubtitlesEnabled(!subtitlesEnabled) },
                    )
                },
                onChannelClick = { onChannelClick(video.channel.id) },
                onFollowClick = { requireLogin(viewModel.follow::toggle) },
                onNotificationLevelSelect = viewModel.follow::selectNotificationLevel,
                onLikeClick = { requireLogin(viewModel::toggleLike) },
                onDislikeClick = { requireLogin(viewModel::toggleDislike) },
                onSaveClick = { requireLogin { showSaveSheet = true } },
                download = download,
                onDownloadClick = {
                    requireLogin {
                        when {
                            download != null -> onOpenDownloads()
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                                PackageManager.PERMISSION_GRANTED ->
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else -> viewModel.startDownload()
                        }
                    }
                },
                onShareClick = { shareVideo(context, video) },
                onCommentsClick = { showComments = true },
                onSuggestionClick = onVideoClick,
                modifier = modifier,
            )
            if (showComments) {
                CommentsSheet(
                    state = commentsState,
                    controller = viewModel.comments,
                    isLoggedIn = isLoggedIn,
                    onLoginRequired = onLoginRequired,
                    onDismiss = { showComments = false },
                )
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
        // Lecteur collé en haut, toujours visible pendant le défilement. En plein écran, il est seul,
        // au même endroit : sa vue n'est pas recréée, la lecture continue sans à-coup.
        player()
        if (!fullscreen) LazyColumn(
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

@Composable
private fun PlayerArea(
    fullscreen: Boolean,
    onFullscreenToggle: (Boolean) -> Unit,
    detail: VideoDetail,
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
    onMenuClick: () -> Unit,
    subtitlesEnabled: Boolean,
    onSubtitlesClick: () -> Unit,
) {
    // Surcouche de nos boutons : en plein écran, elle suit la barre de contrôle de Media3, affichée
    // d'un appui et masquée au second ou après 3 secondes ; en portrait, toujours visible.
    var controlsVisible by remember { mutableStateOf(false) }
    val showOverlay = !fullscreen || controlsVisible
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (fullscreen) Modifier.fillMaxSize() else Modifier.aspectRatio(16f / 9f))
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (detail.videoUrl != null) {
            VideoPlayer(
                player = player,
                fullscreen = fullscreen,
                onFullscreenToggle = onFullscreenToggle,
                onControlsVisibilityChange = { controlsVisible = it },
                modifier = Modifier.fillMaxSize(),
            )
            // Miniature par-dessus le lecteur tant que la première image n'est pas affichée : au
            // lancement, et au retour d'une autre application, pendant que la vidéo se recharge.
            // Un écran noir ferait croire à un plantage.
            if (showThumbnail) {
                AsyncImage(
                    model = detail.video.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Publicité avant la vidéo (ADR-021), en plein lecteur et sans contrôle de lecture.
            if (adState != null) {
                AdPlaybackOverlay(adState, adPlayer, onSkipAd, onRemoveAdsClick, onVisitAdSite, Modifier.fillMaxSize())
            }
            if (autoplay != null) {
                AutoplayOverlay(autoplay, onCancelAutoplay, Modifier.fillMaxSize())
            }
            // Pendant l'annonce en plein écran, rien d'autre qu'« Ignorer » et la bande de l'annonce.
            if (showOverlay && (!fullscreen || adState == null)) {
                // En plein écran, la flèche sort du plein écran avant de quitter la vidéo, comme Retour.
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
        update = { view -> view.setFullscreenButtonState(fullscreen) },
        // La vue est détachée du lecteur quand elle quitte l'écran ; le lecteur, lui, reste au ViewModel.
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
 */
@Composable
private fun rememberFullscreen(): FullscreenState {
    val activity = LocalActivity.current
    val isOn = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val set: (Boolean) -> Unit = { on ->
        activity?.requestedOrientation =
            if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    BackHandler(enabled = isOn) { set(false) }
    if (activity != null) {
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
    onNotificationLevelSelect: (NotificationLevel) -> Unit,
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
            FollowButton(state = followState, onClick = onFollowClick, onNotificationLevelSelect = onNotificationLevelSelect)
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
