package com.youtubeclone.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeclone.app.domain.model.Category
import com.youtubeclone.app.domain.model.User
import com.youtubeclone.app.domain.model.Video
import com.youtubeclone.app.domain.usecase.GetCategoriesUseCase
import com.youtubeclone.app.domain.usecase.GetCurrentUserUseCase
import com.youtubeclone.app.domain.usecase.GetHomeFeedUseCase
import com.youtubeclone.app.domain.usecase.ObserveInterestsUseCase
import com.youtubeclone.app.domain.usecase.ObservePublicationsUseCase
import com.youtubeclone.app.domain.usecase.ObserveSessionUseCase
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

/** Rangée de filtres : « Tout » (catégorie null) puis les catégories du catalogue. */
data class HomeFilters(
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getHomeFeed: GetHomeFeedUseCase,
    private val getCategories: GetCategoriesUseCase,
    private val getCurrentUser: GetCurrentUserUseCase,
    observeInterests: ObserveInterestsUseCase,
    observeSession: ObserveSessionUseCase,
    observePublications: ObservePublicationsUseCase,
    private val firstScreenReadiness: FirstScreenReadiness,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _filters = MutableStateFlow(HomeFilters())
    val filters: StateFlow<HomeFilters> = _filters.asStateFlow()

    // Compte affiché dans la barre du haut ; null pour un visiteur.
    private val _account = MutableStateFlow<User?>(null)
    val account: StateFlow<User?> = _account.asStateFlow()

    private var loadJob: Job? = null

    init {
        // Premier chargement, puis à chaque changement des centres d'intérêt (onglet « Vous »),
        // pour remonter les bonnes vidéos en tête du fil.
        viewModelScope.launch {
            observeInterests().collect { load() }
        }
        viewModelScope.launch {
            observeSession().collect { loggedIn -> loadAccount(loggedIn) }
        }
        // Vidéo publiée depuis l'application : le fil la montre dès le retour sur l'accueil.
        viewModelScope.launch {
            observePublications().collect { load() }
        }
        loadCategories()
    }

    fun load() {
        loadJob?.cancel()
        _uiState.value = HomeUiState.Loading
        loadJob = viewModelScope.launch {
            _uiState.value = try {
                val videos = getHomeFeed(_filters.value.selectedCategoryId)
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

    /** null : « Tout ». */
    fun selectCategory(categoryId: String?) {
        if (categoryId == _filters.value.selectedCategoryId) return
        _filters.value = _filters.value.copy(selectedCategoryId = categoryId)
        load()
    }

    // Sans catégories (réseau, API), la rangée n'affiche que « Tout » : le fil reste utilisable.
    private fun loadCategories() {
        viewModelScope.launch {
            try {
                val categories = getCategories()
                _filters.value = _filters.value.copy(categories = categories)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    // Avatar de la barre du haut. En cas d'échec, on affiche l'icône de compte générique.
    private suspend fun loadAccount(loggedIn: Boolean) {
        _account.value = if (loggedIn) {
            try {
                getCurrentUser()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }
}
