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
import com.youtubeclone.app.domain.model.VideoDetail
import com.youtubeclone.app.domain.usecase.GetChannelPageUseCase
import com.youtubeclone.app.domain.usecase.GetVideoDetailUseCase
import com.youtubeclone.app.domain.usecase.ObserveFollowChangesUseCase
import com.youtubeclone.app.domain.usecase.ObserveSessionUseCase
import com.youtubeclone.app.domain.usecase.RecordViewUseCase
import com.youtubeclone.app.domain.usecase.SetFollowingUseCase
import com.youtubeclone.app.presentation.channel.FollowController
import com.youtubeclone.app.presentation.common.UiError
import com.youtubeclone.app.presentation.common.toUiError
import com.youtubeclone.app.presentation.navigation.WatchRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface WatchUiState {
    data object Loading : WatchUiState
    data class Error(val error: UiError) : WatchUiState
    data class Success(val detail: VideoDetail) : WatchUiState
}

@HiltViewModel
class WatchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext context: Context,
    private val getVideoDetail: GetVideoDetailUseCase,
    private val recordView: RecordViewUseCase,
    private val getChannelPage: GetChannelPageUseCase,
    observeSession: ObserveSessionUseCase,
    setFollowing: SetFollowingUseCase,
    observeFollowChanges: ObserveFollowChangesUseCase,
) : ViewModel() {

    private val videoId = savedStateHandle.toRoute<WatchRoute>().videoId

    private val _uiState = MutableStateFlow<WatchUiState>(WatchUiState.Loading)
    val uiState: StateFlow<WatchUiState> = _uiState.asStateFlow()

    // Le lecteur appartient au ViewModel : il survit aux rotations et est libéré dans onCleared,
    // quand l'écran quitte la pile de navigation.
    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    private var viewRecorded = false

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) recordViewOnce()
        }
    }

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    val follow = FollowController(viewModelScope, setFollowing, observeFollowChanges)

    init {
        player.addListener(playerListener)
        load()
        // Après une connexion demandée par le bouton S'abonner, seul l'état d'abonnement
        // est relu : la vidéo continue sans être rechargée.
        viewModelScope.launch {
            observeSession().collect { loggedIn ->
                _isLoggedIn.value = loggedIn
                (_uiState.value as? WatchUiState.Success)?.let { loadFollowStatus(it.detail.video.channel.id) }
            }
        }
    }

    fun load() {
        _uiState.value = WatchUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                val detail = getVideoDetail(videoId)
                detail.videoUrl?.let(::startPlayback)
                loadFollowStatus(detail.video.channel.id)
                WatchUiState.Success(detail)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                WatchUiState.Error(e.toUiError(notFoundRes = R.string.error_video_not_found))
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

    fun pause() {
        player.pause()
    }

    private fun startPlayback(url: String) {
        player.setMediaItem(MediaItem.fromUri(url))
        player.playWhenReady = true
        player.prepare()
    }

    // Une vue par ouverture de l'écran, au premier démarrage effectif de la lecture.
    // Un échec d'envoi est ignoré : il ne doit pas gêner la lecture.
    private fun recordViewOnce() {
        if (viewRecorded) return
        viewRecorded = true
        viewModelScope.launch {
            try {
                recordView(videoId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    override fun onCleared() {
        player.removeListener(playerListener)
        player.release()
    }
}
