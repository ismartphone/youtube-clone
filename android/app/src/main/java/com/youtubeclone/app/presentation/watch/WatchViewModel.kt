package com.youtubeclone.app.presentation.watch

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.toRoute
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.analytics.AdPlacement
import com.youtubeclone.app.domain.analytics.Analytics
import com.youtubeclone.app.domain.model.DataError
import com.youtubeclone.app.domain.model.DownloadProblem
import com.youtubeclone.app.domain.model.DownloadedVideo
import com.youtubeclone.app.domain.model.LiveVideoEvent
import com.youtubeclone.app.domain.model.MAX_DOWNLOADS
import com.youtubeclone.app.domain.model.Video
import com.youtubeclone.app.domain.model.VideoAd
import com.youtubeclone.app.domain.model.VideoDetail
import com.youtubeclone.app.domain.model.VideoFormat
import com.youtubeclone.app.domain.usecase.DownloadVideoUseCase
import com.youtubeclone.app.domain.usecase.GetChannelPageUseCase
import com.youtubeclone.app.domain.usecase.GetCommentsUseCase
import com.youtubeclone.app.domain.usecase.GetSuggestedVideosUseCase
import com.youtubeclone.app.domain.usecase.GetVideoDetailUseCase
import com.youtubeclone.app.domain.usecase.ObserveAutoplayUseCase
import com.youtubeclone.app.domain.usecase.ObserveDownloadsUseCase
import com.youtubeclone.app.domain.usecase.ObserveFollowChangesUseCase
import com.youtubeclone.app.domain.usecase.ObserveLiveVideoUseCase
import com.youtubeclone.app.domain.usecase.ObservePremiumStatusUseCase
import com.youtubeclone.app.domain.usecase.ObserveSessionUseCase
import com.youtubeclone.app.domain.usecase.PostCommentUseCase
import com.youtubeclone.app.domain.usecase.RecordViewUseCase
import com.youtubeclone.app.domain.usecase.SetAutoplayUseCase
import com.youtubeclone.app.domain.usecase.SetDislikedUseCase
import com.youtubeclone.app.domain.usecase.SetFollowingUseCase
import com.youtubeclone.app.domain.usecase.SetLikedUseCase
import com.youtubeclone.app.domain.usecase.SetNotificationLevelUseCase
import com.youtubeclone.app.playback.BackgroundPlayback
import com.youtubeclone.app.presentation.ads.AdPlaybackController
import com.youtubeclone.app.presentation.channel.FollowController
import com.youtubeclone.app.presentation.common.UiError
import com.youtubeclone.app.presentation.common.toUiError
import com.youtubeclone.app.presentation.navigation.WatchRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface WatchUiState {
    data object Loading : WatchUiState
    data class Error(val error: UiError) : WatchUiState
    data class Success(val detail: VideoDetail) : WatchUiState
    /** Vidéo exclusive sans abonnement Premium en cours : l'écran ouvre le paywall. */
    data object PremiumRequired : WatchUiState
}

/** Décompte affiché avant d'enchaîner sur la vidéo suggérée (lecture automatique). */
private const val AUTOPLAY_SECONDS = 5

/**
 * Qualité proposée dans le menu du lecteur, désignée par le petit côté de l'image (720p...).
 * [group] et [trackIndex] désignent la piste correspondante, pour l'imposer au lecteur.
 */
data class QualityOption(val size: Int, val group: TrackGroup, val trackIndex: Int)

/** Vidéo suivante de la lecture automatique, et secondes restantes avant de l'ouvrir. */
data class AutoplayCountdown(val next: Video, val secondsLeft: Int)

/** Compteurs affichés sous le lecteur, mis à jour en direct (ADR-004). */
data class LiveStats(
    val viewCount: Int,
    val likeCount: Int,
    val isLiked: Boolean,
    val isDisliked: Boolean = false,
    /** Appel « j'aime » ou « je n'aime pas » en cours : les boutons ignorent les doubles clics. */
    val likePending: Boolean = false,
)

@HiltViewModel
class WatchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val getVideoDetail: GetVideoDetailUseCase,
    private val recordView: RecordViewUseCase,
    private val getChannelPage: GetChannelPageUseCase,
    private val setLiked: SetLikedUseCase,
    private val setDisliked: SetDislikedUseCase,
    observeDownloads: ObserveDownloadsUseCase,
    private val downloadVideo: DownloadVideoUseCase,
    private val observeLiveVideo: ObserveLiveVideoUseCase,
    observeSession: ObserveSessionUseCase,
    setFollowing: SetFollowingUseCase,
    setNotificationLevel: SetNotificationLevelUseCase,
    observeFollowChanges: ObserveFollowChangesUseCase,
    getComments: GetCommentsUseCase,
    private val getSuggestedVideos: GetSuggestedVideosUseCase,
    postComment: PostCommentUseCase,
    private val observePremium: ObservePremiumStatusUseCase,
    private val backgroundPlayback: BackgroundPlayback,
    private val observeAutoplay: ObserveAutoplayUseCase,
    private val setAutoplay: SetAutoplayUseCase,
    analytics: Analytics,
) : ViewModel() {

    val videoId = savedStateHandle.toRoute<WatchRoute>().videoId

    // Publicité avant la vidéo (ADR-021), choisie par le serveur ; la vidéo démarre à sa fin.
    val ads = AdPlaybackController(context, viewModelScope, analytics, AdPlacement.Preroll)

    // La vidéo elle-même a démarré (après l'éventuelle publicité).
    private var videoStarted = false

    private val _uiState = MutableStateFlow<WatchUiState>(WatchUiState.Loading)
    val uiState: StateFlow<WatchUiState> = _uiState.asStateFlow()

    private val _stats = MutableStateFlow<LiveStats?>(null)
    val stats: StateFlow<LiveStats?> = _stats.asStateFlow()

    // Vidéos suggérées sous la vidéo ; liste vide tant qu'elles ne sont pas chargées.
    private val _suggestions = MutableStateFlow<List<Video>>(emptyList())
    val suggestions: StateFlow<List<Video>> = _suggestions.asStateFlow()

    /** Téléchargement de cette vidéo (ADR-019), ou null si elle n'est pas téléchargée. */
    val download: StateFlow<DownloadedVideo?> = observeDownloads()
        .map { list -> list.firstOrNull { it.videoId == videoId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Téléchargement refusé faute de Premium : l'écran ouvre l'offre.
    private val _downloadNeedsPremium = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val downloadNeedsPremium: SharedFlow<Unit> = _downloadNeedsPremium.asSharedFlow()

    // Qualités réellement disponibles pour cette vidéo, de la meilleure à la plus faible.
    private val _qualities = MutableStateFlow<List<QualityOption>>(emptyList())
    val qualities: StateFlow<List<QualityOption>> = _qualities.asStateFlow()

    // Qualité imposée au lecteur ; null : « Auto », le lecteur choisit selon le débit (HLS).
    private val _selectedQuality = MutableStateFlow<Int?>(null)
    val selectedQuality: StateFlow<Int?> = _selectedQuality.asStateFlow()

    // Sous-titres affichés ; désactivés au départ, activés par le bouton du lecteur ou le menu.
    private val _subtitlesEnabled = MutableStateFlow(false)
    val subtitlesEnabled: StateFlow<Boolean> = _subtitlesEnabled.asStateFlow()

    /** Réglage « Lecture automatique », partagé avec Vous → Paramètres ; null tant qu'il n'est pas lu. */
    val autoplayEnabled: StateFlow<Boolean?> = observeAutoplay()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Décompte de la lecture automatique ; null hors décompte.
    private val _autoplay = MutableStateFlow<AutoplayCountdown?>(null)
    val autoplay: StateFlow<AutoplayCountdown?> = _autoplay.asStateFlow()

    // Vidéo à ouvrir à la fin du décompte.
    private val _autoplayNext = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val autoplayNext: SharedFlow<String> = _autoplayNext.asSharedFlow()
    private var autoplayJob: Job? = null
    private var isScreenVisible = true

    private val _errors = MutableSharedFlow<UiError>(extraBufferCapacity = 1)
    val errors: SharedFlow<UiError> = _errors.asSharedFlow()

    // Le lecteur appartient au ViewModel : il survit aux rotations et est libéré dans onCleared,
    // quand l'écran quitte la pile de navigation.
    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    private var viewRecorded = false

    // Vidéo confiée au lecteur, décrite dans la mesure de lecture.
    private var playingVideo: Video? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                recordViewOnce()
                // Relancée (retour en arrière dans la vidéo, rejouer) : plus de vidéo suivante.
                cancelAutoplay()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) startAutoplay()
        }

        // Pistes connues (manifeste HLS lu, ou MP4 ouvert) : la liste des qualités suit.
        override fun onTracksChanged(tracks: Tracks) {
            _qualities.value = tracks.groups
                .filter { it.type == C.TRACK_TYPE_VIDEO }
                .flatMap { group ->
                    (0 until group.length)
                        .filter { group.isTrackSupported(it) }
                        .map { index ->
                            val format = group.getTrackFormat(index)
                            QualityOption(minOf(format.width, format.height), group.mediaTrackGroup, index)
                        }
                }
                .filter { it.size > 0 }
                .distinctBy { it.size }
                .sortedByDescending { it.size }
        }
    }

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    val follow = FollowController(viewModelScope, setFollowing, observeFollowChanges, setNotificationLevel)
    val comments = CommentsController(viewModelScope, videoId, getComments, postComment)

    // Première connexion temps réel déjà établie : les suivantes sont des reconnexions.
    private var hasConnectedOnce = false

    init {
        player.addListener(playerListener)
        // Abonnement Premium pris pendant la publicité (lien « Supprimer les publicités ») :
        // elle s'arrête et la vidéo démarre.
        viewModelScope.launch {
            observePremium().collect { if (it?.isPremium == true) ads.cancel() }
        }
        load()
        comments.loadFirstPage()
        // Après une connexion demandée par S'abonner, J'aime ou un commentaire, seuls les états
        // propres à l'utilisateur sont relus : la vidéo continue sans être rechargée.
        viewModelScope.launch {
            observeSession().collect { loggedIn ->
                _isLoggedIn.value = loggedIn
                (_uiState.value as? WatchUiState.Success)?.let {
                    loadFollowStatus(it.detail.video.channel.id)
                    refreshStats()
                }
            }
        }
    }

    fun load() {
        _uiState.value = WatchUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                val detail = getVideoDetail(videoId)
                playingVideo = detail.video
                // HLS quand il existe (choix de la qualité), MP4 sinon (ADR-017).
                when {
                    detail.hlsUrl != null -> startPlayback(detail.hlsUrl, isHls = true, detail.subtitleUrl, detail.ad)
                    detail.videoUrl != null -> startPlayback(detail.videoUrl, isHls = false, detail.subtitleUrl, detail.ad)
                }
                showStats(detail)
                loadSuggestions(detail.video)
                loadFollowStatus(detail.video.channel.id)
                WatchUiState.Success(detail)
            } catch (e: CancellationException) {
                throw e
            } catch (_: DataError.PremiumRequired) {
                WatchUiState.PremiumRequired
            } catch (e: Exception) {
                WatchUiState.Error(e.toUiError(notFoundRes = R.string.error_video_not_found))
            }
        }
    }

    /**
     * Événements en direct de la vidéo. Appelée par l'écran tant qu'il est visible :
     * la connexion WebSocket s'ouvre avec la collecte et se ferme quand l'écran est quitté.
     */
    suspend fun collectLiveUpdates() {
        observeLiveVideo(videoId).collect { event ->
            when (event) {
                LiveVideoEvent.Connected -> {
                    // Après une coupure ou un retour sur l'écran, on rattrape ce qui a été manqué.
                    if (hasConnectedOnce) {
                        refreshStats()
                        comments.loadFirstPage()
                    }
                    hasConnectedOnce = true
                }
                is LiveVideoEvent.CommentAdded -> comments.onCommentReceived(event.comment)
                is LiveVideoEvent.ViewsUpdated -> _stats.update { it?.copy(viewCount = event.viewCount) }
                is LiveVideoEvent.LikesUpdated -> _stats.update { it?.copy(likeCount = event.likeCount) }
            }
        }
    }

    /** À n'appeler qu'avec une session ouverte : sinon l'écran redirige vers la connexion. */
    fun toggleLike() {
        val current = _stats.value ?: return
        if (current.likePending) return
        _stats.value = current.copy(likePending = true)
        viewModelScope.launch {
            try {
                val status = setLiked(videoId, like = !current.isLiked)
                _stats.update {
                    it?.copy(
                        likeCount = status.likeCount,
                        isLiked = status.isLiked,
                        isDisliked = status.isDisliked,
                        likePending = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _stats.update { it?.copy(likePending = false) }
                _errors.tryEmit(e.toUiError())
            }
        }
    }

    /** Bouton Télécharger : le serveur autorise d'abord ; sans Premium, l'offre s'ouvre. */
    fun startDownload() {
        val video = (_uiState.value as? WatchUiState.Success)?.detail?.video ?: return
        viewModelScope.launch {
            try {
                downloadVideo(video)
            } catch (e: CancellationException) {
                throw e
            } catch (_: DataError.PremiumRequired) {
                _downloadNeedsPremium.tryEmit(Unit)
            } catch (_: DownloadProblem.TooMany) {
                _errors.tryEmit(UiError(R.string.downloads_too_many, code = MAX_DOWNLOADS))
            } catch (_: DownloadProblem.NotEnoughSpace) {
                _errors.tryEmit(UiError(R.string.downloads_no_space))
            } catch (e: Exception) {
                _errors.tryEmit(e.toUiError())
            }
        }
    }

    /** « Je n'aime pas » : remplace le « j'aime » éventuel, dont le compteur baisse alors. */
    fun toggleDislike() {
        val current = _stats.value ?: return
        if (current.likePending) return
        _stats.value = current.copy(likePending = true)
        viewModelScope.launch {
            try {
                val status = setDisliked(videoId, dislike = !current.isDisliked)
                _stats.update {
                    it?.copy(
                        likeCount = status.likeCount,
                        isLiked = status.isLiked,
                        isDisliked = status.isDisliked,
                        likePending = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _stats.update { it?.copy(likePending = false) }
                _errors.tryEmit(e.toUiError())
            }
        }
    }

    /**
     * Écran masqué. Application en arrière-plan : un compte Premium garde le son (lecture en
     * arrière-plan, via [BackgroundPlayback]). Dans tous les autres cas, notamment une autre
     * vidéo ouverte par-dessus, la lecture se met en pause, sans libérer le lecteur.
     */
    fun onScreenStopped(appInBackground: Boolean) {
        isScreenVisible = false
        cancelAutoplay()
        if (!appInBackground || observePremium().value?.isPremium != true) player.pause()
        ads.pause()
    }

    /**
     * Écran de nouveau visible, par exemple au retour d'une autre vidéo qui avait pris la
     * lecture en arrière-plan : ce lecteur la reprend.
     */
    fun onScreenStarted() {
        isScreenVisible = true
        ads.resume()
        if (videoStarted && observePremium().value?.isPremium == true) {
            backgroundPlayback.start(player)
        }
    }

    /**
     * Menu du lecteur : [size] null rend la main au lecteur (« Auto ») ; sinon la qualité de ce
     * petit côté lui est imposée, jusqu'à nouvel ordre.
     */
    fun selectQuality(size: Int?) {
        val option = _qualities.value.firstOrNull { it.size == size }
        val parameters = player.trackSelectionParameters.buildUpon()
        player.trackSelectionParameters = if (option == null) {
            parameters.clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
        } else {
            parameters.setOverrideForType(TrackSelectionOverride(option.group, option.trackIndex)).build()
        }
        _selectedQuality.value = option?.size
        // Changement de résolution en cours de lecture : certains décodeurs logiciels (celui de
        // l'émulateur, notamment) gardent leur configuration et affichent une image brouillée. Le
        // lecteur repart donc à la même position, ce qui recrée ses décodeurs pour la nouvelle
        // qualité, au prix d'une courte remise en mémoire tampon.
        val position = player.currentPosition
        val playWhenReady = player.playWhenReady
        player.stop()
        player.prepare()
        player.seekTo(position)
        player.playWhenReady = playWhenReady
    }

    /** Bouton des sous-titres du lecteur, ou interrupteur du menu. */
    fun setSubtitlesEnabled(enabled: Boolean) {
        _subtitlesEnabled.value = enabled
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
            .build()
    }

    /** Interrupteur du menu du lecteur : même réglage que Vous → Paramètres. */
    fun onAutoplayChange(enabled: Boolean) {
        viewModelScope.launch { setAutoplay(enabled) }
        if (!enabled) cancelAutoplay()
    }

    /** Bouton Annuler du décompte, ou lecture relancée : la vidéo reste à l'écran. */
    fun cancelAutoplay() {
        autoplayJob?.cancel()
        autoplayJob = null
        _autoplay.value = null
    }

    /**
     * Fin de la vidéo : décompte puis ouverture de la première vidéo suggérée, si le réglage est
     * activé. Jamais pour un Short, qui boucle déjà, ni quand l'écran n'est pas visible
     * (application en arrière-plan, autre écran par-dessus).
     */
    private fun startAutoplay() {
        val video = playingVideo ?: return
        val next = _suggestions.value.firstOrNull() ?: return
        if (video.format == VideoFormat.SHORT || !isScreenVisible) return
        autoplayJob?.cancel()
        autoplayJob = viewModelScope.launch {
            if (!observeAutoplay().first()) return@launch
            for (left in AUTOPLAY_SECONDS downTo 1) {
                _autoplay.value = AutoplayCountdown(next, left)
                delay(1_000)
            }
            _autoplay.value = null
            _autoplayNext.tryEmit(next.id)
        }
    }

    private fun showStats(detail: VideoDetail) {
        _stats.value = LiveStats(
            viewCount = detail.video.viewCount,
            likeCount = detail.likeStatus.likeCount,
            isLiked = detail.likeStatus.isLiked,
            isDisliked = detail.likeStatus.isDisliked,
        )
    }

    // Suggestions sous la vidéo. Un échec laisse simplement la liste vide : la lecture continue.
    private fun loadSuggestions(current: Video) {
        viewModelScope.launch {
            _suggestions.value = try {
                getSuggestedVideos(current)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    // Relit les compteurs sans relancer la lecture. Un échec garde les valeurs affichées.
    private fun refreshStats() {
        viewModelScope.launch {
            try {
                showStats(getVideoDetail(videoId))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    // L'état d'abonnement vient de la page de chaîne. En cas d'échec, le bouton reste masqué
    // sans gêner la lecture.
    // Amélioration possible : cette page renvoie aussi toutes les vidéos de la chaîne, inutiles ici.
    // Une route légère (abonnés et statut seulement) suffirait quand le catalogue grandira.
    private fun loadFollowStatus(channelId: String) {
        viewModelScope.launch {
            try {
                val page = getChannelPage(channelId)
                follow.show(channelId, page.followStatus, canFollow = !page.isOwner)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    // La vidéo ne démarre qu'après la publicité [ad], choisie par le serveur : aucune pour un
    // compte Premium ou un Short (ADR-021). Elle n'est préparée qu'ensuite : deux décodeurs vidéo
    // ouverts en même temps figent l'annonce sur certains appareils (constaté sur l'émulateur).
    private fun startPlayback(url: String, isHls: Boolean, subtitleUrl: String?, ad: VideoAd?) {
        // Media3 lit le HLS et adapte la qualité au débit ; une qualité choisie lui est imposée.
        // Les sous-titres WebVTT sont une piste à part, ajoutée à la vidéo (HLS ou MP4).
        val item = MediaItem.Builder()
            .setUri(url)
            .setMimeType(if (isHls) MimeTypes.APPLICATION_M3U8 else null)
            .setSubtitleConfigurations(
                listOfNotNull(
                    subtitleUrl?.let {
                        MediaItem.SubtitleConfiguration.Builder(Uri.parse(it))
                            .setMimeType(MimeTypes.TEXT_VTT)
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .build()
                    },
                ),
            )
            .build()
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !_subtitlesEnabled.value)
            .build()
        player.setMediaItem(item)
        player.playWhenReady = false
        if (ad != null) ads.play(ad, onFinished = ::startVideo) else startVideo()
    }

    private fun startVideo() {
        player.prepare()
        videoStarted = true
        if (observePremium().value?.isPremium == true) backgroundPlayback.start(player)
        // Publicité finie pendant que l'écran était masqué : la vidéo attend le retour.
        player.playWhenReady = isScreenVisible
    }

    // Une vue par ouverture de l'écran, au premier démarrage effectif de la lecture.
    // Un échec d'envoi est ignoré : il ne doit pas gêner la lecture.
    private fun recordViewOnce() {
        val video = playingVideo ?: return
        if (viewRecorded) return
        viewRecorded = true
        viewModelScope.launch {
            try {
                recordView(video)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    override fun onCleared() {
        player.removeListener(playerListener)
        backgroundPlayback.stop(player)
        player.release()
        ads.release()
    }
}
