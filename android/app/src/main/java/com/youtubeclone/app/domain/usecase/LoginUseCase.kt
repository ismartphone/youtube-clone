package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.analytics.Analytics
import com.youtubeclone.app.domain.analytics.AnalyticsEvent
import com.youtubeclone.app.domain.repository.AuthRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Ouvre une session avec un email et un mot de passe. */
class LoginUseCase @Inject constructor(
    private val repository: AuthRepository,
    private val analytics: Analytics,
) {
    suspend operator fun invoke(email: String, password: String) {
        val previous = repository.currentUserId.first()
        repository.login(email, password)
        // Compte ajouté alors qu'un autre était actif (ADR-022) : les mesures repartent de zéro.
        if (previous != null && previous != repository.currentUserId.first()) analytics.reset()
        identifySession(repository, analytics)
        analytics.track(AnalyticsEvent.LoggedIn)
    }
}

/** Rattache les mesures au compte qui vient d'ouvrir sa session, par son seul UUID (ADR-013). */
internal suspend fun identifySession(repository: AuthRepository, analytics: Analytics) {
    repository.currentUserId.first()?.let(analytics::identify)
}
