package com.youtubeclone.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeclone.app.domain.model.Video
import com.youtubeclone.app.domain.model.VideoFormat
import com.youtubeclone.app.domain.usecase.GetVideosUseCase
import com.youtubeclone.app.presentation.common.UiError
import com.youtubeclone.app.presentation.common.toUiError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Empty : HomeUiState
    data class Error(val error: UiError) : HomeUiState
    data class Success(val videos: List<Video>) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getVideos: GetVideosUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = HomeUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                // Le fil d'accueil ne montre que les vidéos classiques, les Shorts ont leur onglet.
                val page = getVideos(format = VideoFormat.LONG)
                if (page.items.isEmpty()) HomeUiState.Empty else HomeUiState.Success(page.items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                HomeUiState.Error(e.toUiError())
            }
        }
    }
}
