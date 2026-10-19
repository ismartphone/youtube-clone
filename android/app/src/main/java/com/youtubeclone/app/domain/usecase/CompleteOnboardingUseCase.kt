package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.repository.PreferencesRepository
import javax.inject.Inject

/** Marque l'écran d'accueil du premier lancement comme vu : il ne s'affichera plus. */
class CompleteOnboardingUseCase @Inject constructor(
    private val repository: PreferencesRepository,
) {
    suspend operator fun invoke() = repository.completeOnboarding()
}
