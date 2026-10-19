package com.youtubeclone.app.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeclone.app.domain.usecase.CompleteOnboardingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val completeOnboarding: CompleteOnboardingUseCase,
) : ViewModel() {

    /** L'écran est enregistré comme vu ; l'application passe alors au fil d'accueil (AppRoot). */
    fun complete() {
        viewModelScope.launch { completeOnboarding() }
    }
}
