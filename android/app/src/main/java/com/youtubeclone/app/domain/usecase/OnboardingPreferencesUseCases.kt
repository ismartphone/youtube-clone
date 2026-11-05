package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.model.VideoFormat
import com.youtubeclone.app.domain.repository.PreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

// Préférences du parcours d'accueil (ADR-028), propres à l'appareil.

/** Format préféré (étape « format »), qui décide de l'onglet d'arrivée ; null sans choix. */
class ObservePreferredFormatUseCase @Inject constructor(
    private val repository: PreferencesRepository,
) {
    operator fun invoke(): Flow<VideoFormat?> = repository.preferredFormat
}

class SetPreferredFormatUseCase @Inject constructor(
    private val repository: PreferencesRepository,
) {
    suspend operator fun invoke(format: VideoFormat?) = repository.setPreferredFormat(format)
}

/** Réglage « Sons et vibrations » du parcours d'accueil. */
class ObserveOnboardingFeedbackUseCase @Inject constructor(
    private val repository: PreferencesRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.onboardingFeedback
}

class SetOnboardingFeedbackUseCase @Inject constructor(
    private val repository: PreferencesRepository,
) {
    suspend operator fun invoke(enabled: Boolean) = repository.setOnboardingFeedback(enabled)
}
