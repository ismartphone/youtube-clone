package com.youtubeclone.app.presentation.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeclone.app.domain.model.PlaylistSummary
import com.youtubeclone.app.domain.usecase.CreatePlaylistUseCase
import com.youtubeclone.app.domain.usecase.GetPlaylistsUseCase
import com.youtubeclone.app.domain.usecase.SetVideoInPlaylistUseCase
import com.youtubeclone.app.presentation.common.UiError
import com.youtubeclone.app.presentation.common.toUiError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SaveToPlaylistUiState {
    data object Loading : SaveToPlaylistUiState
    data class Error(val error: UiError) : SaveToPlaylistUiState
    /** [pending] : playlists dont l'ajout ou le retrait est en cours. */
    data class Ready(val playlists: List<PlaylistSummary>, val pending: Set<String> = emptySet()) : SaveToPlaylistUiState
}

/**
 * Feuille « Enregistrer » de l'écran de lecture (ADR-019) : une case par playlist, cochée si elle
 * contient la vidéo. Cocher l'ajoute, décocher la retire ; une nouvelle playlist reçoit la vidéo.
 */
@HiltViewModel
class SaveToPlaylistViewModel @Inject constructor(
    private val getPlaylists: GetPlaylistsUseCase,
    private val createPlaylist: CreatePlaylistUseCase,
    private val setVideoInPlaylist: SetVideoInPlaylistUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SaveToPlaylistUiState>(SaveToPlaylistUiState.Loading)
    val uiState: StateFlow<SaveToPlaylistUiState> = _uiState.asStateFlow()

    private val _errors = MutableSharedFlow<UiError>(extraBufferCapacity = 1)
    val errors: SharedFlow<UiError> = _errors.asSharedFlow()

    private var videoId: String? = null

    /** Relu à chaque ouverture de la feuille. */
    fun load(videoId: String) {
        this.videoId = videoId
        _uiState.value = SaveToPlaylistUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                SaveToPlaylistUiState.Ready(getPlaylists(videoId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SaveToPlaylistUiState.Error(e.toUiError())
            }
        }
    }

    fun toggle(playlist: PlaylistSummary) {
        val video = videoId ?: return
        val state = _uiState.value as? SaveToPlaylistUiState.Ready ?: return
        if (playlist.id in state.pending) return
        val add = !playlist.containsVideo
        updateReady { it.copy(pending = it.pending + playlist.id) }
        viewModelScope.launch {
            try {
                setVideoInPlaylist(playlist.id, video, add)
                updateReady { ready ->
                    ready.copy(
                        playlists = ready.playlists.map {
                            if (it.id == playlist.id) it.copy(containsVideo = add, videoCount = it.videoCount + if (add) 1 else -1) else it
                        },
                        pending = ready.pending - playlist.id,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateReady { it.copy(pending = it.pending - playlist.id) }
                _errors.tryEmit(e.toUiError())
            }
        }
    }

    /** Nouvelle playlist, qui reçoit aussitôt la vidéo regardée. */
    fun create(title: String) {
        val video = videoId ?: return
        if (title.isBlank()) return
        viewModelScope.launch {
            try {
                val created = createPlaylist(title)
                setVideoInPlaylist(created.id, video, true)
                updateReady { ready ->
                    ready.copy(playlists = listOf(created.copy(containsVideo = true, videoCount = 1)) + ready.playlists)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errors.tryEmit(e.toUiError())
            }
        }
    }

    private fun updateReady(transform: (SaveToPlaylistUiState.Ready) -> SaveToPlaylistUiState.Ready) {
        _uiState.update { if (it is SaveToPlaylistUiState.Ready) transform(it) else it }
    }
}
