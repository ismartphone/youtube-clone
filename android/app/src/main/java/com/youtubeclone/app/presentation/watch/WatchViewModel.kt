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
import com.youtubeclone.app.domain.usecase.GetVideoDetailUseCase
import com.youtubeclone.app.domain.usecase.RecordViewUseCase
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

    init {
        player.addListener(playerListener)
        load()
    }

    fun load() {
        _uiState.value = WatchUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                val detail = getVideoDetail(videoId)
                detail.videoUrl?.let(::startPlayback)
                WatchUiState.Success(detail)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                WatchUiState.Error(e.toUiError(notFoundRes = R.string.error_video_not_found))
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
