package com.youtubeclone.app.playback.download

import com.youtubeclone.app.domain.repository.DownloadsRepository
import com.youtubeclone.app.domain.usecase.ObserveSessionUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Suit la session pour les téléchargements (ADR-019) : à chaque ouverture de session, au
 * démarrage de l'application compris, les vidéos téléchargées sont revalidées par le serveur ;
 * à la déconnexion, elles sont supprimées (décisions 10 et 14).
 */
@Singleton
class DownloadsSync @Inject constructor(
    private val observeSession: ObserveSessionUseCase,
    private val downloads: DownloadsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        scope.launch {
            observeSession().collect { loggedIn ->
                runCatching { if (loggedIn) downloads.revalidate() else downloads.removeAll() }
            }
        }
    }
}
