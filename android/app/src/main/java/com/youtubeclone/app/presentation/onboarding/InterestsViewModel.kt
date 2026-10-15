package com.youtubeclone.app.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeclone.app.domain.model.Category
import com.youtubeclone.app.domain.usecase.GetCategoriesUseCase
import com.youtubeclone.app.domain.usecase.ObserveInterestsUseCase
import com.youtubeclone.app.domain.usecase.SaveInterestsUseCase
import com.youtubeclone.app.presentation.common.UiError
import com.youtubeclone.app.presentation.common.toUiError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CategoriesState {
    data object Loading : CategoriesState
    data class Error(val error: UiError) : CategoriesState
    data class Loaded(val categories: List<Category>) : CategoriesState
}

data class InterestsUiState(
    val categories: CategoriesState = CategoriesState.Loading,
    val selected: Set<String> = emptySet(),
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
)

/** Choix des centres d'intérêt, au premier lancement comme depuis l'onglet « Vous ». */
@HiltViewModel
class InterestsViewModel @Inject constructor(
    private val getCategories: GetCategoriesUseCase,
    private val observeInterests: ObserveInterestsUseCase,
    private val saveInterests: SaveInterestsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InterestsUiState())
    val uiState: StateFlow<InterestsUiState> = _uiState.asStateFlow()

    // L'utilisateur a déjà coché ou décoché : la lecture des choix enregistrés ne doit pas l'écraser.
    private var changedByUser = false

    init {
        // Les choix déjà enregistrés sont cochés d'avance (aucun au premier lancement).
        viewModelScope.launch {
            val saved = observeInterests().first()
            if (!changedByUser) _uiState.update { it.copy(selected = saved) }
        }
        loadCategories()
    }

    fun loadCategories() {
        _uiState.update { it.copy(categories = CategoriesState.Loading) }
        viewModelScope.launch {
            val categories = try {
                CategoriesState.Loaded(getCategories())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CategoriesState.Error(e.toUiError())
            }
            _uiState.update { it.copy(categories = categories) }
        }
    }

    fun toggle(categoryId: String) {
        changedByUser = true
        _uiState.update {
            it.copy(selected = if (categoryId in it.selected) it.selected - categoryId else it.selected + categoryId)
        }
    }

    /**
     * Enregistre la sélection, même vide : le choix est facultatif. Au premier lancement,
     * marque aussi l'écran d'accueil comme vu ; l'application passe alors au fil d'accueil.
     */
    fun save(completesOnboarding: Boolean) {
        val current = _uiState.value
        if (current.isSaving) return
        // Une catégorie supprimée depuis le dernier choix n'est pas conservée.
        val known = (current.categories as? CategoriesState.Loaded)?.categories?.mapTo(HashSet()) { it.id }
        val selected = if (known != null) current.selected intersect known else current.selected
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            saveInterests(selected, completesOnboarding)
            _uiState.update { it.copy(isSaving = false, isSaved = true) }
        }
    }
}
