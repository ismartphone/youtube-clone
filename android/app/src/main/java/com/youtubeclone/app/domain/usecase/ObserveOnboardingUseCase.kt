package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.repository.PreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Indique si l'écran d'accueil du premier lancement a déjà été vu. */
class ObserveOnboardingUseCase @Inject constructor(
    private val repository: PreferencesRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.onboardingCompleted
}
