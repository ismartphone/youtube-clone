package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.analytics.Analytics
import com.youtubeclone.app.domain.analytics.AnalyticsEvent
import com.youtubeclone.app.domain.repository.AuthRepository
import javax.inject.Inject

/** Crée le compte puis ouvre directement la session : le serveur ne le fait pas à l'inscription. */
class RegisterUseCase @Inject constructor(
    private val repository: AuthRepository,
    private val analytics: Analytics,
) {
    suspend operator fun invoke(email: String, password: String, displayName: String) {
        repository.register(email, password, displayName)
        repository.login(email, password)
        identifySession(repository, analytics)
        analytics.track(AnalyticsEvent.SignedUp)
    }
}
