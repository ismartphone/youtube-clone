package com.youtubeclone.app.presentation.home

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.DataError
import com.youtubeclone.app.domain.model.Video
import com.youtubeclone.app.domain.model.VideoFormat
import com.youtubeclone.app.domain.usecase.GetVideosUseCase
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
    // Message à afficher, avec le code HTTP éventuel comme argument de la chaîne.
    data class Error(@StringRes val messageRes: Int, val code: Int? = null) : HomeUiState
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
                e.toErrorState()
            }
        }
    }

    private fun Exception.toErrorState(): HomeUiState.Error = when (this) {
        DataError.Network -> HomeUiState.Error(R.string.error_network)
        is DataError.Server -> HomeUiState.Error(R.string.error_server, code)
        else -> HomeUiState.Error(R.string.error_unexpected)
    }
}
