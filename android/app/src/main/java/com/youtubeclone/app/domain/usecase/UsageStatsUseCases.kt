package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.repository.PreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

// Réglage « Partager des statistiques d'usage » (ADR-013), propre à l'appareil.

class ObserveShareUsageStatsUseCase @Inject constructor(
    private val repository: PreferencesRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.shareUsageStats
}

class SetShareUsageStatsUseCase @Inject constructor(
    private val repository: PreferencesRepository,
) {
    suspend operator fun invoke(enabled: Boolean) = repository.setShareUsageStats(enabled)
}
