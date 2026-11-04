package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.analytics.Analytics
import com.youtubeclone.app.domain.analytics.AnalyticsEvent
import com.youtubeclone.app.domain.repository.AuthRepository
import com.youtubeclone.app.domain.repository.DownloadsRepository
import com.youtubeclone.app.domain.repository.PushTokenRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Déconnecte le compte actif, et lui seul (ADR-022) : ses tokens, sa clé et ses téléchargements
 * disparaissent de l'appareil. S'il reste un compte dont la session est valide, on bascule sur lui ;
 * sinon on repart en visiteur.
 */
class LogoutUseCase @Inject constructor(
    private val repository: AuthRepository,
    private val analytics: Analytics,
    private val pushTokens: PushTokenRepository,
    private val downloads: DownloadsRepository,
    private val switchAccount: SwitchAccountUseCase,
) {
    // L'événement part encore au nom du compte ; les suivants redeviennent anonymes.
    // Le jeton de notification est retiré tant que la session permet d'appeler le serveur (ADR-020).
    suspend operator fun invoke() {
        val userId = repository.currentUserId.first() ?: return
        analytics.track(AnalyticsEvent.LoggedOut)
        pushTokens.unregister()
        downloads.removeAccount(userId)
        repository.logout()
        analytics.reset()
        repository.accounts.first().firstOrNull { !it.needsReconnection }?.let { switchAccount(it.userId) }
    }
}
