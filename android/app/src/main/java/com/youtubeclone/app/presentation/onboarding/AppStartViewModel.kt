package com.youtubeclone.app.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeclone.app.domain.model.VideoFormat
import com.youtubeclone.app.domain.usecase.ObserveOnboardingUseCase
import com.youtubeclone.app.domain.usecase.ObservePreferredFormatUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface AppStartState {
    /** Lecture des préférences en cours : rien n'est encore affiché sous l'animation d'ouverture. */
    data object Loading : AppStartState

    /** Tout premier lancement : parcours d'accueil (ADR-028). */
    data object Onboarding : AppStartState

    /** [startOnShorts] : le format préféré choisi dans le parcours est « Shorts » ; l'onglet Shorts est alors le premier affiché. */
    data class Ready(val startOnShorts: Boolean) : AppStartState
}

@HiltViewModel
class AppStartViewModel @Inject constructor(
    observeOnboarding: ObserveOnboardingUseCase,
    observePreferredFormat: ObservePreferredFormatUseCase,
    firstScreenReadiness: FirstScreenReadiness,
) : ViewModel() {

    // Passe de Onboarding à Ready dès que le parcours d'accueil est enregistré comme terminé.
    val state: StateFlow<AppStartState> = combine(observeOnboarding(), observePreferredFormat()) { completed, format ->
        if (completed) AppStartState.Ready(startOnShorts = format == VideoFormat.SHORT) else AppStartState.Onboarding
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppStartState.Loading)

    /**
     * L'application peut se montrer : préférences lues et premier écran chargé.
     * L'animation d'ouverture attend ce moment avant de s'effacer.
     */
    val isAppReady: StateFlow<Boolean> = combine(state, firstScreenReadiness.isReady) { start, firstScreenLoaded ->
        start != AppStartState.Loading && firstScreenLoaded
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
}
