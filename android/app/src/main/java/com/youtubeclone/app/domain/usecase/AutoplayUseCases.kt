package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.repository.PreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

// Réglage « Lecture automatique », propre à l'appareil.

class ObserveAutoplayUseCase @Inject constructor(
    private val repository: PreferencesRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.autoplay
}

class SetAutoplayUseCase @Inject constructor(
    private val repository: PreferencesRepository,
) {
    suspend operator fun invoke(enabled: Boolean) = repository.setAutoplay(enabled)
}
