package com.youtubeclone.app.presentation.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.youtubeclone.app.domain.model.Video
import com.youtubeclone.app.domain.model.VideoPage
import com.youtubeclone.app.domain.usecase.GetLikedVideosUseCase
import com.youtubeclone.app.domain.usecase.GetWatchHistoryUseCase
import com.youtubeclone.app.presentation.common.UiError
import com.youtubeclone.app.presentation.common.toUiError
import com.youtubeclone.app.presentation.navigation.YouListRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface YouListUiState {
    data object Loading : YouListUiState
    /** La liste est réellement vide. */
    data object Empty : YouListUiState
    data class Error(val error: UiError) : YouListUiState
    data class Success(
        val videos: List<Video>,
        val canLoadMore: Boolean,
        val isLoadingMore: Boolean = false,
    ) : YouListUiState
}

@HiltViewModel
class YouListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getWatchHistory: GetWatchHistoryUseCase,
    private val getLikedVideos: GetLikedVideosUseCase,
) : ViewModel() {

    val list: YouList = YouList.valueOf(savedStateHandle.toRoute<YouListRoute>().list)

    private val _uiState = MutableStateFlow<YouListUiState>(YouListUiState.Loading)
    val uiState: StateFlow<YouListUiState> = _uiState.asStateFlow()

    // Échecs du chargement des pages suivantes : la liste déjà affichée reste en place.
    private val _errors = MutableSharedFlow<UiError>(extraBufferCapacity = 1)
    val errors: SharedFlow<UiError> = _errors.asSharedFlow()

    private var loadJob: Job? = null
    private var lastPage = 0

    /**
     * Charge la première page. Appelée à chaque retour sur l'écran : une vidéo regardée ou
     * aimée entre-temps doit apparaître en tête. Une liste déjà affichée reste visible pendant ce temps.
     */
    fun refresh() {
        loadJob?.cancel()
        if (_uiState.value !is YouListUiState.Success) _uiState.value = YouListUiState.Loading
        loadJob = viewModelScope.launch {
            _uiState.value = try {
                val page = fetch(1)
                lastPage = 1
                if (page == null || page.items.isEmpty()) {
                    YouListUiState.Empty
                } else {
                    YouListUiState.Success(page.items, canLoadMore = page.hasMore())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                YouListUiState.Error(e.toUiError())
            }
        }
    }

    fun loadMore() {
        val current = _uiState.value as? YouListUiState.Success ?: return
        if (!current.canLoadMore || current.isLoadingMore) return
        _uiState.value = current.copy(isLoadingMore = true)
        loadJob = viewModelScope.launch {
            try {
                val page = fetch(lastPage + 1) ?: return@launch
                lastPage = page.page
                _uiState.update {
                    // Une vidéo peut changer de page entre deux appels (revue, aimée) : pas de doublon.
                    val videos = (current.videos + page.items).distinctBy(Video::id)
                    YouListUiState.Success(videos, canLoadMore = page.hasMore())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { (it as? YouListUiState.Success)?.copy(isLoadingMore = false) ?: it }
                _errors.tryEmit(e.toUiError())
            }
        }
    }

    // Vos vidéos n'a pas de liste à charger : sans chaîne, l'écran reste sur son état vide.
    private suspend fun fetch(page: Int): VideoPage? = when (list) {
        YouList.History -> getWatchHistory(page)
        YouList.Liked -> getLikedVideos(page)
        YouList.YourVideos -> null
    }

    private fun VideoPage.hasMore() = page * pageSize < total
}
