package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.analytics.Analytics
import com.youtubeclone.app.domain.analytics.AnalyticsEvent
import com.youtubeclone.app.domain.repository.AuthRepository
import com.youtubeclone.app.domain.repository.PushTokenRepository
import javax.inject.Inject

class LogoutUseCase @Inject constructor(
    private val repository: AuthRepository,
    private val analytics: Analytics,
    private val pushTokens: PushTokenRepository,
) {
    // L'événement part encore au nom du compte ; les suivants redeviennent anonymes.
    // Le jeton de notification est retiré tant que la session permet d'appeler le serveur (ADR-020).
    suspend operator fun invoke() {
        analytics.track(AnalyticsEvent.LoggedOut)
        pushTokens.unregister()
        repository.logout()
        analytics.reset()
    }
}
