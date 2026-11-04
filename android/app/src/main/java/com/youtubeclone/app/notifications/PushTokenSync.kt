package com.youtubeclone.app.notifications

import com.youtubeclone.app.domain.repository.PushTokenRepository
import com.youtubeclone.app.domain.usecase.ObserveSessionUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Enregistre le jeton de l'appareil auprès du serveur à chaque ouverture de session, au démarrage
 * de l'application compris (ADR-020). Le retrait, lui, se fait avant la déconnexion (LogoutUseCase),
 * tant que la session permet encore d'appeler le serveur.
 */
@Singleton
class PushTokenSync @Inject constructor(
    private val observeSession: ObserveSessionUseCase,
    private val pushTokens: PushTokenRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        scope.launch {
            observeSession().collect { loggedIn ->
                // Hors ligne : le prochain démarrage ou le prochain renouvellement réessaiera.
                if (loggedIn) runCatching { pushTokens.register() }
            }
        }
    }
}
