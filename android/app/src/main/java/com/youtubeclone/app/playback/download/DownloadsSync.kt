package com.youtubeclone.app.playback.download

import com.youtubeclone.app.domain.repository.DownloadsRepository
import com.youtubeclone.app.domain.usecase.ObserveActiveAccountUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Suit le compte actif pour les téléchargements (ADR-019, ADR-022). Au démarrage, les
 * téléchargements d'avant l'ADR-022 sont attribués au compte actif, ou supprimés sans session
 * (décision 4). À chaque ouverture de session, démarrage et bascule compris, ceux du compte actif
 * sont revalidés par le serveur (décision 9). La suppression à la déconnexion, elle, ne concerne
 * que le compte qui se déconnecte : elle passe par LogoutUseCase et RemoveAccountUseCase.
 */
@Singleton
class DownloadsSync @Inject constructor(
    private val observeActiveAccount: ObserveActiveAccountUseCase,
    private val downloads: DownloadsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        scope.launch {
            var first = true
            observeActiveAccount().collect { userId ->
                runCatching {
                    if (first) downloads.adoptUnowned(userId)
                    if (userId != null) downloads.revalidate()
                }
                first = false
            }
        }
    }
}
