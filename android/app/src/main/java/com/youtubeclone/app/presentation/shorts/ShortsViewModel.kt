package com.youtubeclone.app.presentation.shorts

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.Video
import com.youtubeclone.app.domain.model.VideoDetail
import com.youtubeclone.app.domain.model.VideoFormat
import com.youtubeclone.app.domain.usecase.GetVideoDetailUseCase
import com.youtubeclone.app.domain.usecase.GetVideosUseCase
import com.youtubeclone.app.domain.usecase.RecordViewUseCase
import com.youtubeclone.app.presentation.common.UiError
import com.youtubeclone.app.presentation.common.toUiError
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
)

@HiltViewModel
class ShortsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val getVideos: GetVideosUseCase,
    private val getVideoDetail: GetVideoDetailUseCase,
    private val recordView: RecordViewUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ShortsUiState>(ShortsUiState.Loading)
    val uiState: StateFlow<ShortsUiState> = _uiState.asStateFlow()

    private val _playback = MutableStateFlow(ShortsPlayback())
    val playback: StateFlow<ShortsPlayback> = _playback.asStateFlow()

    // Un seul lecteur pour toutes les pages, qui n'existe que lorsque l'onglet est affiché :
    // il est libéré quand on change d'onglet, puis recréé au retour.
    private val _player = MutableStateFlow<ExoPlayer?>(null)
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

    init {
        load()
    }

    fun load() {
        _uiState.value = ShortsUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                val page = getVideos(format = VideoFormat.SHORT)
                if (page.items.isEmpty()) ShortsUiState.Empty else ShortsUiState.Success(page.items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ShortsUiState.Error(e.toUiError())
            }
        }
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
        recordViewOnce(video.id)

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
                _playback.update { it.copy(blockedReason = e.toUiError()) }
            }
            // Préchargement du détail suivant, pour un glissement plus fluide.
            videos.getOrNull(page + 1)?.let { detailOf(it.id) }
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

    /** Application en arrière-plan : simple pause, le lecteur est gardé. */
    fun onBackground() {
        _player.value?.pause()
    }

    /** Retour au premier plan : reprise, sauf si l'utilisateur avait mis en pause. */
    fun onForeground() {
        if (!_playback.value.pausedByUser) _player.value?.playWhenReady = true
    }

    private fun loadCurrent(player: ExoPlayer) {
        val url = currentUrl ?: return
        player.setMediaItem(MediaItem.fromUri(url), resumePositionMs)
        player.prepare()
        player.playWhenReady = !_playback.value.pausedByUser
    }

    private fun detailOf(id: String): Deferred<VideoDetail> =
        details.getOrPut(id) {
            viewModelScope.async { getVideoDetail(id) }.also { deferred ->
                // Un échec n'est pas gardé en cache : il sera retenté au prochain passage.
                deferred.invokeOnCompletion { error -> if (error != null) details.remove(id) }
            }
        }

    // Une vue par vidéo, dès qu'elle devient visible. Un échec d'envoi est ignoré.
    private fun recordViewOnce(id: String) {
        if (!viewedIds.add(id)) return
        viewModelScope.launch {
            try {
                recordView(id)
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
