package com.youtubeclone.app.presentation.shorts

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.Channel
import com.youtubeclone.app.domain.model.DataError
import com.youtubeclone.app.domain.model.Video
import com.youtubeclone.app.domain.model.VideoDetail
import com.youtubeclone.app.domain.model.VideoFormat
import com.youtubeclone.app.domain.usecase.GetChannelPageUseCase
import com.youtubeclone.app.domain.usecase.CommentUseCases
import com.youtubeclone.app.domain.usecase.GetVideoDetailUseCase
import com.youtubeclone.app.domain.usecase.GetVideosUseCase
import com.youtubeclone.app.domain.usecase.ObserveFollowChangesUseCase
import com.youtubeclone.app.domain.usecase.ObserveSessionUseCase
import com.youtubeclone.app.domain.usecase.RecordViewUseCase
import com.youtubeclone.app.domain.usecase.SetDislikedUseCase
import com.youtubeclone.app.domain.usecase.SetFollowingUseCase
import com.youtubeclone.app.domain.usecase.SetLikedUseCase
import com.youtubeclone.app.presentation.channel.FollowController
import com.youtubeclone.app.presentation.common.UiError
import com.youtubeclone.app.presentation.common.toUiError
import com.youtubeclone.app.presentation.watch.CommentsController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ShortsUiState {
    data object Loading : ShortsUiState
    data object Empty : ShortsUiState
    data class Error(val error: UiError) : ShortsUiState
    data class Success(val videos: List<Video>) : ShortsUiState
}

/** État de lecture de la page visible. */
data class ShortsPlayback(
    val page: Int = -1,
    // Pause demandée par un tap ; la pause due à l'arrière-plan n'est pas comptée ici.
    val pausedByUser: Boolean = false,
    // Message affiché à la place de la vidéo : accès refusé ou chargement impossible.
    val blockedReason: UiError? = null,
    // Short exclusif sans abonnement Premium : le message propose de découvrir l'offre.
    val premiumRequired: Boolean = false,
)

/** « J'aime » d'un Short, connu une fois son détail chargé. */
data class ShortLikes(
    val likeCount: Int,
    val isLiked: Boolean,
    val isDisliked: Boolean = false,
    /** Appel en cours : le bouton ignore les nouveaux appuis. */
    val isPending: Boolean = false,
)

@HiltViewModel
class ShortsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext context: Context,
    private val getVideos: GetVideosUseCase,
    private val getVideoDetail: GetVideoDetailUseCase,
    private val recordView: RecordViewUseCase,
    private val setLiked: SetLikedUseCase,
    private val setDisliked: SetDislikedUseCase,
    private val getChannelPage: GetChannelPageUseCase,
    private val commentUseCases: CommentUseCases,
    observeSession: ObserveSessionUseCase,
    setFollowing: SetFollowingUseCase,
    observeFollowChanges: ObserveFollowChangesUseCase,
) : ViewModel() {

    // Short d'ouverture (ShortsPlayerRoute, depuis une page de chaîne) ; null pour l'onglet Shorts.
    private val startVideoId: String? = savedStateHandle["startVideoId"]

    // Chaîne de l'utilisateur, si le Short affiché en est : il peut y mettre un cœur (ADR-023).
    private var ownedChannelId: String? = null

    private val _uiState = MutableStateFlow<ShortsUiState>(ShortsUiState.Loading)
    val uiState: StateFlow<ShortsUiState> = _uiState.asStateFlow()

    private val _playback = MutableStateFlow(ShortsPlayback())
    val playback: StateFlow<ShortsPlayback> = _playback.asStateFlow()

    // « J'aime » par identifiant de vidéo, remplis au chargement des détails.
    private val _likes = MutableStateFlow<Map<String, ShortLikes>>(emptyMap())
    val likes: StateFlow<Map<String, ShortLikes>> = _likes.asStateFlow()

    // Commentaires du Short visible : un contrôleur neuf à chaque changement de page.
    private val _comments = MutableStateFlow<CommentsController?>(null)
    val comments: StateFlow<CommentsController?> = _comments.asStateFlow()

    // Bouton S'abonner : [follow] décrit la chaîne [followChannelId], null tant qu'elle n'est pas lue.
    val follow = FollowController(viewModelScope, setFollowing, observeFollowChanges)
    private val _followChannelId = MutableStateFlow<String?>(null)
    val followChannelId: StateFlow<String?> = _followChannelId.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _errors = MutableSharedFlow<UiError>(extraBufferCapacity = 1)
    val errors: SharedFlow<UiError> = _errors.asSharedFlow()

    // Un seul lecteur pour toutes les pages, qui n'existe que lorsque l'onglet est affiché :
    // il est libéré quand on change d'onglet, puis recréé au retour.
    private val _player = MutableStateFlow<ExoPlayer?>(null)

    // Première image du Short courant affichée. Avant, la miniature recouvre le lecteur, dont la
    // vue vidéo est noire : au lancement, au changement de Short et au retour de l'arrière-plan.
    private val _firstFrameShown = MutableStateFlow(false)
    val firstFrameShown: StateFlow<Boolean> = _firstFrameShown.asStateFlow()

    private val firstFrameListener = object : Player.Listener {
        override fun onRenderedFirstFrame() {
            _firstFrameShown.value = true
        }
    }
    val player: StateFlow<Player?> = _player.asStateFlow()
    private val appContext = context.applicationContext

    // Vidéo de la page visible et position, pour reprendre au même endroit après un retour.
    private var currentUrl: String? = null
    private var resumePositionMs = 0L

    // La liste ne contient pas l'URL des fichiers : le détail est demandé page par page,
    // et le serveur y vérifie l'accès. Les détails déjà chargés sont gardés en mémoire.
    private val details = mutableMapOf<String, Deferred<VideoDetail>>()
    private val viewedIds = mutableSetOf<String>()
    private var playJob: Job? = null
    private var followJob: Job? = null

    init {
        load()
        // Connexion ou déconnexion : les états propres à l'utilisateur (j'aime, abonnement)
        // sont relus pour le Short visible, sans relancer la lecture.
        viewModelScope.launch {
            var first = true
            observeSession().collect { loggedIn ->
                _isLoggedIn.value = loggedIn
                if (!first) refreshUserState()
                first = false
            }
        }
    }

    fun load() {
        _uiState.value = ShortsUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                val videos = startVideoId?.let { startWith(it) } ?: getVideos(format = VideoFormat.SHORT).items
                if (videos.isEmpty()) ShortsUiState.Empty else ShortsUiState.Success(videos)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ShortsUiState.Error(e.toUiError())
            }
        }
    }

    // Le Short demandé en première page, puis les autres Shorts du fil. Son détail est chargé
    // tout de suite (il sert aussi à la lecture) : il peut ne pas figurer dans la première page du fil.
    private suspend fun startWith(videoId: String): List<Video> {
        val start = detailOf(videoId).await().video
        val others = getVideos(format = VideoFormat.SHORT).items.filter { it.id != videoId }
        return listOf(start) + others
    }

    /** La page visible commence à changer : la vidéo qui sort de l'écran se met en pause. */
    fun onPageLeaving(page: Int) {
        if (page != _playback.value.page) _player.value?.pause()
    }

    /** Une page est devenue la page visible, une fois le glissement terminé. */
    fun onPageSettled(page: Int) {
        val videos = (_uiState.value as? ShortsUiState.Success)?.videos ?: return
        val video = videos.getOrNull(page) ?: return
        if (page == _playback.value.page) return

        _playback.value = ShortsPlayback(page = page)
        recordViewOnce(video)
        _comments.value = CommentsController(viewModelScope, video.id, commentUseCases).also {
            it.setCreator(video.channel, isOwner = ownedChannelId == video.channel.id)
            it.loadFirstPage()
        }
        loadFollowStatus(video.channel)

        playJob?.cancel()
        currentUrl = null
        resumePositionMs = 0L
        _player.value?.run {
            stop()
            clearMediaItems()
        }
        playJob = viewModelScope.launch {
            try {
                val url = detailOf(video.id).await().videoUrl
                if (url == null) {
                    _playback.update { it.copy(blockedReason = UiError(R.string.watch_premium_only)) }
                } else {
                    currentUrl = url
                    _player.value?.let(::loadCurrent)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _playback.update {
                    it.copy(blockedReason = e.toUiError(), premiumRequired = e is DataError.PremiumRequired)
                }
            }
            // Préchargement du détail suivant, pour un glissement plus fluide.
            videos.getOrNull(page + 1)?.let { detailOf(it.id) }
        }
    }

    /** Même route que l'écran de lecture. À n'appeler qu'avec une session ouverte. */
    fun toggleLike(videoId: String) {
        val current = _likes.value[videoId] ?: return
        if (current.isPending) return
        _likes.update { it + (videoId to current.copy(isPending = true)) }
        viewModelScope.launch {
            try {
                val status = setLiked(videoId, like = !current.isLiked)
                _likes.update { it + (videoId to ShortLikes(status.likeCount, status.isLiked, status.isDisliked)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _likes.update { it + (videoId to current) }
                _errors.tryEmit(e.toUiError())
            }
        }
    }

    /** « Je n'aime pas » (ADR-019) : remplace le « j'aime » éventuel. À n'appeler qu'avec une session. */
    fun toggleDislike(videoId: String) {
        val current = _likes.value[videoId] ?: return
        if (current.isPending) return
        _likes.update { it + (videoId to current.copy(isPending = true)) }
        viewModelScope.launch {
            try {
                val status = setDisliked(videoId, dislike = !current.isDisliked)
                _likes.update { it + (videoId to ShortLikes(status.likeCount, status.isLiked, status.isDisliked)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _likes.update { it + (videoId to current) }
                _errors.tryEmit(e.toUiError())
            }
        }
    }

    fun togglePlayPause() {
        val pausedByUser = !_playback.value.pausedByUser
        _playback.update { it.copy(pausedByUser = pausedByUser) }
        _player.value?.playWhenReady = !pausedByUser
    }

    /** Onglet affiché : création du lecteur et reprise de la vidéo en cours. */
    fun onShown() {
        if (_player.value != null) return
        val player = ExoPlayer.Builder(appContext).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            addListener(firstFrameListener)
        }
        _player.value = player
        loadCurrent(player)
    }

    /** Onglet quitté : position mémorisée, lecteur libéré. */
    fun onHidden() {
        val player = _player.value ?: return
        resumePositionMs = player.currentPosition
        _player.value = null
        player.release()
    }

    /**
     * Application en arrière-plan : pause, et piste vidéo coupée, ce qui libère son décodeur. Gardé
     * pendant que la surface d'affichage est détruite puis recréée, il affiche une image corrompue
     * au retour (constaté sur l'émulateur). Le lecteur lui-même est gardé.
     */
    fun onBackground() {
        val player = _player.value ?: return
        player.pause()
        setVideoTrackDisabled(player, true)
        _firstFrameShown.value = false
    }

    /** Retour au premier plan : l'image revient et la lecture reprend, sauf pause de l'utilisateur. */
    fun onForeground() {
        val player = _player.value ?: return
        setVideoTrackDisabled(player, false)
        if (!_playback.value.pausedByUser) player.playWhenReady = true
    }

    private fun setVideoTrackDisabled(player: Player, disabled: Boolean) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, disabled)
            .build()
    }

    private fun loadCurrent(player: ExoPlayer) {
        val url = currentUrl ?: return
        _firstFrameShown.value = false
        player.setMediaItem(MediaItem.fromUri(url), resumePositionMs)
        player.prepare()
        player.playWhenReady = !_playback.value.pausedByUser
    }

    private fun detailOf(id: String): Deferred<VideoDetail> =
        details.getOrPut(id) {
            viewModelScope.async {
                getVideoDetail(id).also { detail ->
                    _likes.update {
                        it + (id to ShortLikes(detail.likeStatus.likeCount, detail.likeStatus.isLiked, detail.likeStatus.isDisliked))
                    }
                }
            }.also { deferred ->
                // Un échec n'est pas gardé en cache : il sera retenté au prochain passage.
                deferred.invokeOnCompletion { error -> if (error != null) details.remove(id) }
            }
        }

    // L'état d'abonnement vient de la page de chaîne. En cas d'échec, le bouton reste masqué.
    // Le propriétaire de la chaîne, lui, peut mettre un cœur sur les commentaires (ADR-023).
    private fun loadFollowStatus(channel: Channel) {
        val channelId = channel.id
        if (_followChannelId.value == channelId) return
        _followChannelId.value = null
        followJob?.cancel()
        followJob = viewModelScope.launch {
            try {
                val page = getChannelPage(channelId)
                follow.show(channelId, page.followStatus, canFollow = !page.isOwner)
                _followChannelId.value = channelId
                ownedChannelId = channelId.takeIf { page.isOwner }
                _comments.value?.setCreator(channel, isOwner = page.isOwner)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    private fun refreshUserState() {
        val videos = (_uiState.value as? ShortsUiState.Success)?.videos ?: return
        val video = videos.getOrNull(_playback.value.page) ?: return
        // Les détails gardés en mémoire contiennent l'ancien état « j'aime ».
        details.clear()
        _likes.value = emptyMap()
        detailOf(video.id)
        _followChannelId.value = null
        loadFollowStatus(video.channel)
        // Avis de l'utilisateur sur chaque commentaire (ADR-023).
        _comments.value?.loadFirstPage()
    }

    // Une vue par vidéo, dès qu'elle devient visible. Un échec d'envoi est ignoré.
    private fun recordViewOnce(video: Video) {
        if (!viewedIds.add(video.id)) return
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
        _player.value?.release()
    }
}
