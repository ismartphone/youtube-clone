package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.repository.PreferencesRepository
import javax.inject.Inject

/**
 * Enregistre les centres d'intérêt. Au premier lancement ([completesOnboarding] = true),
 * marque aussi l'écran d'accueil comme vu, pour ne plus l'afficher.
 */
class SaveInterestsUseCase @Inject constructor(
    private val repository: PreferencesRepository,
) {
    suspend operator fun invoke(interests: Set<String>, completesOnboarding: Boolean) {
        if (completesOnboarding) {
            repository.completeOnboarding(interests)
        } else {
            repository.setInterests(interests)
        }
    }
}
