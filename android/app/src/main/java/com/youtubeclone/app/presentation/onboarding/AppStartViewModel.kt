package com.youtubeclone.app.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeclone.app.domain.usecase.ObserveOnboardingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface AppStartState {
    /** Lecture des préférences en cours : rien n'est encore affiché sous l'animation d'ouverture. */
    data object Loading : AppStartState

    /** Tout premier lancement : écran d'accueil et choix des centres d'intérêt. */
    data object Onboarding : AppStartState

    data object Ready : AppStartState
}

@HiltViewModel
class AppStartViewModel @Inject constructor(
    observeOnboarding: ObserveOnboardingUseCase,
    firstScreenReadiness: FirstScreenReadiness,
) : ViewModel() {

    // Passe de Onboarding à Ready dès que l'écran d'accueil est enregistré comme vu.
    val state: StateFlow<AppStartState> = observeOnboarding()
        .map { completed -> if (completed) AppStartState.Ready else AppStartState.Onboarding }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppStartState.Loading)

    /**
     * L'application peut se montrer : préférences lues et premier écran chargé.
     * L'animation d'ouverture attend ce moment avant de s'effacer.
     */
    val isAppReady: StateFlow<Boolean> = combine(state, firstScreenReadiness.isReady) { start, firstScreenLoaded ->
        start != AppStartState.Loading && firstScreenLoaded
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
}
