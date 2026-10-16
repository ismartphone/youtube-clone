package com.youtubeclone.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeclone.app.domain.model.Video
import com.youtubeclone.app.domain.usecase.GetHomeFeedUseCase
import com.youtubeclone.app.domain.usecase.ObserveInterestsUseCase
import com.youtubeclone.app.presentation.common.UiError
import com.youtubeclone.app.presentation.common.toUiError
import com.youtubeclone.app.presentation.onboarding.FirstScreenReadiness
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    private val getHomeFeed: GetHomeFeedUseCase,
    observeInterests: ObserveInterestsUseCase,
    private val firstScreenReadiness: FirstScreenReadiness,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        // Premier chargement, puis à chaque changement des centres d'intérêt (onglet « Vous »),
        // pour remonter les bonnes vidéos en tête du fil.
        viewModelScope.launch {
            observeInterests().collect { load() }
        }
    }

    fun load() {
        loadJob?.cancel()
        _uiState.value = HomeUiState.Loading
        loadJob = viewModelScope.launch {
            _uiState.value = try {
                val videos = getHomeFeed()
                if (videos.isEmpty()) HomeUiState.Empty else HomeUiState.Success(videos)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                HomeUiState.Error(e.toUiError())
            }
            // Premier chargement terminé, réussi ou non : l'animation d'ouverture peut s'effacer.
            firstScreenReadiness.markReady()
        }
    }
}
