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
        repository.login(email, password)
        identifySession(repository, analytics)
        analytics.track(AnalyticsEvent.LoggedIn)
    }
}

/** Rattache les mesures au compte qui vient d'ouvrir sa session, par son seul UUID (ADR-013). */
internal suspend fun identifySession(repository: AuthRepository, analytics: Analytics) {
    repository.currentUserId.first()?.let(analytics::identify)
}
