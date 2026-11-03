package com.youtubeclone.app.presentation.watch

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.toRoute
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.DataError
import com.youtubeclone.app.domain.model.LiveVideoEvent
import com.youtubeclone.app.domain.model.Video
import com.youtubeclone.app.domain.model.VideoDetail
import com.youtubeclone.app.domain.usecase.GetChannelPageUseCase
import com.youtubeclone.app.domain.usecase.GetCommentsUseCase
import com.youtubeclone.app.domain.usecase.GetSuggestedVideosUseCase
import com.youtubeclone.app.domain.usecase.GetVideoDetailUseCase
import com.youtubeclone.app.domain.usecase.IsPremiumUseCase
import com.youtubeclone.app.domain.usecase.ObservePremiumStatusUseCase
import com.youtubeclone.app.domain.usecase.ObserveFollowChangesUseCase
import com.youtubeclone.app.domain.usecase.ObserveLiveVideoUseCase
import com.youtubeclone.app.domain.usecase.ObserveSessionUseCase
import com.youtubeclone.app.domain.usecase.PostCommentUseCase
import com.youtubeclone.app.domain.usecase.RecordViewUseCase
import com.youtubeclone.app.domain.usecase.SetFollowingUseCase
import com.youtubeclone.app.domain.usecase.SetLikedUseCase
import com.youtubeclone.app.playback.BackgroundPlayback
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface WatchUiState {
    data object Loading : WatchUiState
    data class Error(val error: UiError) : WatchUiState
    data class Success(val detail: VideoDetail) : WatchUiState
    /** Vidéo exclusive sans abonnement Premium en cours : l'écran ouvre le paywall. */
    data object PremiumRequired : WatchUiState
}

/** Durée de la publicité simulée avant la lecture, pour les comptes non Premium (ADR-007). */
private const val AD_SECONDS = 5

/** Compteurs affichés sous le lecteur, mis à jour en direct (ADR-004). */
data class LiveStats(
    val viewCount: Int,
    val likeCount: Int,
    val isLiked: Boolean,
    /** Appel « j'aime » en cours : le bouton est désactivé pour éviter les doubles clics. */
    val likePending: Boolean = false,
)

@HiltViewModel
class WatchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext context: Context,
    private val getVideoDetail: GetVideoDetailUseCase,
    private val recordView: RecordViewUseCase,
    private val getChannelPage: GetChannelPageUseCase,
    private val setLiked: SetLikedUseCase,
    private val observeLiveVideo: ObserveLiveVideoUseCase,
    observeSession: ObserveSessionUseCase,
    setFollowing: SetFollowingUseCase,
    observeFollowChanges: ObserveFollowChangesUseCase,
    getComments: GetCommentsUseCase,
    private val getSuggestedVideos: GetSuggestedVideosUseCase,
    postComment: PostCommentUseCase,
    private val isPremium: IsPremiumUseCase,
    private val observePremium: ObservePremiumStatusUseCase,
    private val backgroundPlayback: BackgroundPlayback,
) : ViewModel() {

    val videoId = savedStateHandle.toRoute<WatchRoute>().videoId

    // Secondes restantes de la publicité simulée ; null quand aucune n'est affichée.
    private val _adSecondsLeft = MutableStateFlow<Int?>(null)
    val adSecondsLeft: StateFlow<Int?> = _adSecondsLeft.asStateFlow()
    private var playbackJob: Job? = null

    private val _uiState = MutableStateFlow<WatchUiState>(WatchUiState.Loading)
    val uiState: StateFlow<WatchUiState> = _uiState.asStateFlow()

    private val _stats = MutableStateFlow<LiveStats?>(null)
    val stats: StateFlow<LiveStats?> = _stats.asStateFlow()

    // Vidéos suggérées sous la vidéo ; liste vide tant qu'elles ne sont pas chargées.
    private val _suggestions = MutableStateFlow<List<Video>>(emptyList())
    val suggestions: StateFlow<List<Video>> = _suggestions.asStateFlow()

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
            if (isPlaying) recordViewOnce()
        }
    }

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    val follow = FollowController(viewModelScope, setFollowing, observeFollowChanges)
    val comments = CommentsController(viewModelScope, videoId, getComments, postComment)

    // Première connexion temps réel déjà établie : les suivantes sont des reconnexions.
    private var hasConnectedOnce = false

    init {
        player.addListener(playerListener)
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
                detail.videoUrl?.let(::startPlayback)
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
                    it?.copy(likeCount = status.likeCount, isLiked = status.isLiked, likePending = false)
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
        if (!appInBackground || observePremium().value?.isPremium != true) player.pause()
    }

    /**
     * Écran de nouveau visible, par exemple au retour d'une autre vidéo qui avait pris la
     * lecture en arrière-plan : ce lecteur la reprend.
     */
    fun onScreenStarted() {
        if (playbackJob?.isCompleted == true && observePremium().value?.isPremium == true) {
            backgroundPlayback.start(player)
        }
    }

    private fun showStats(detail: VideoDetail) {
        _stats.value = LiveStats(
            viewCount = detail.video.viewCount,
            likeCount = detail.likeStatus.likeCount,
            isLiked = detail.likeStatus.isLiked,
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

    // La vidéo se charge tout de suite mais ne démarre qu'après la publicité simulée, que les
    // comptes Premium ne voient pas. Un abonnement pris pendant la publicité l'interrompt.
    private fun startPlayback(url: String) {
        player.setMediaItem(MediaItem.fromUri(url))
        player.playWhenReady = false
        player.prepare()
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            if (!isPremium()) {
                for (left in AD_SECONDS downTo 1) {
                    if (observePremium().value?.isPremium == true) break
                    _adSecondsLeft.value = left
                    delay(1_000)
                }
                _adSecondsLeft.value = null
            }
            if (observePremium().value?.isPremium == true) backgroundPlayback.start(player)
            player.playWhenReady = true
        }
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
    }
}
