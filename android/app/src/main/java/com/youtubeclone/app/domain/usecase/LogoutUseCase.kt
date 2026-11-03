package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.analytics.Analytics
import com.youtubeclone.app.domain.analytics.AnalyticsEvent
import com.youtubeclone.app.domain.repository.AuthRepository
import javax.inject.Inject

class LogoutUseCase @Inject constructor(
    private val repository: AuthRepository,
    private val analytics: Analytics,
) {
    // L'événement part encore au nom du compte ; les suivants redeviennent anonymes.
    suspend operator fun invoke() {
        analytics.track(AnalyticsEvent.LoggedOut)
        repository.logout()
        analytics.reset()
    }
}
