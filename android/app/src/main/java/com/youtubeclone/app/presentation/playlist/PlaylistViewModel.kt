package com.youtubeclone.app.presentation.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.PlaylistDetail
import com.youtubeclone.app.domain.usecase.DeletePlaylistUseCase
import com.youtubeclone.app.domain.usecase.GetPlaylistUseCase
import com.youtubeclone.app.domain.usecase.RenamePlaylistUseCase
import com.youtubeclone.app.domain.usecase.SetVideoInPlaylistUseCase
import com.youtubeclone.app.presentation.common.UiError
import com.youtubeclone.app.presentation.common.toUiError
import com.youtubeclone.app.presentation.navigation.PlaylistRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PlaylistUiState {
    data object Loading : PlaylistUiState
    data class Error(val error: UiError) : PlaylistUiState
    data class Success(val playlist: PlaylistDetail) : PlaylistUiState
}

/** Écran d'une playlist (ADR-019) : ses vidéos, renommer, supprimer, retirer une vidéo. */
@HiltViewModel
class PlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getPlaylist: GetPlaylistUseCase,
    private val renamePlaylist: RenamePlaylistUseCase,
    private val deletePlaylist: DeletePlaylistUseCase,
    private val setVideoInPlaylist: SetVideoInPlaylistUseCase,
) : ViewModel() {

    private val playlistId = savedStateHandle.toRoute<PlaylistRoute>().playlistId

    private val _uiState = MutableStateFlow<PlaylistUiState>(PlaylistUiState.Loading)
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    private val _errors = MutableSharedFlow<UiError>(extraBufferCapacity = 1)
    val errors: SharedFlow<UiError> = _errors.asSharedFlow()

    // Playlist supprimée : l'écran se ferme.
    private val _deleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val deleted: SharedFlow<Unit> = _deleted.asSharedFlow()

    init {
        load()
    }

    fun load() {
        if (_uiState.value !is PlaylistUiState.Success) _uiState.value = PlaylistUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                PlaylistUiState.Success(getPlaylist(playlistId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PlaylistUiState.Error(e.toUiError(notFoundRes = R.string.playlist_not_found))
            }
        }
    }

    fun rename(title: String) = run {
        _uiState.value = PlaylistUiState.Success(renamePlaylist(playlistId, title))
    }

    fun delete() = run {
        deletePlaylist(playlistId)
        _deleted.tryEmit(Unit)
    }

    fun removeVideo(videoId: String) = run {
        setVideoInPlaylist(playlistId, videoId, false)
        val current = (_uiState.value as? PlaylistUiState.Success)?.playlist ?: return@run
        _uiState.value = PlaylistUiState.Success(current.copy(videos = current.videos.filterNot { it.id == videoId }))
    }

    private fun run(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errors.tryEmit(e.toUiError(notFoundRes = R.string.playlist_not_found))
            }
        }
    }
}
