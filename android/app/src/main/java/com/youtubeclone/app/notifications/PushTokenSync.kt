package com.youtubeclone.app.notifications

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.core.content.getSystemService
import com.youtubeclone.app.domain.repository.PushTokenRepository
import com.youtubeclone.app.domain.usecase.ObserveActiveAccountUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Enregistre le jeton de l'appareil auprès du serveur pour le compte actif (ADR-020, ADR-022), à
 * chaque ouverture de session : démarrage, connexion et bascule. L'enregistrement réattribue le
 * jeton au nouveau compte, que l'ancien perd du même coup. Le retrait, lui, se fait avant la
 * déconnexion (LogoutUseCase), tant que la session permet encore d'appeler le serveur.
 *
 * Bascule hors réseau (décision 3) : le jeton est supprimé de l'appareil, pour que l'ancien compte
 * ne reçoive plus rien, puis l'enregistrement est rejoué dès que le réseau revient.
 */
@Singleton
class PushTokenSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val observeActiveAccount: ObserveActiveAccountUseCase,
    private val pushTokens: PushTokenRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        scope.launch {
            var previous: String? = null
            // Une nouvelle bascule annule l'enregistrement en attente du compte précédent.
            observeActiveAccount().collectLatest { userId ->
                val switched = previous != null && userId != null && userId != previous
                previous = userId
                if (userId == null) return@collectLatest
                if (runCatching { pushTokens.register() }.isSuccess) return@collectLatest
                if (switched) pushTokens.forgetLocally()
                if (runCatching { networkAvailable().first() }.isFailure) return@collectLatest
                runCatching { pushTokens.register() }
            }
        }
    }

    // Émet dès qu'un réseau est disponible.
    private fun networkAvailable() = callbackFlow {
        val manager = context.getSystemService<ConnectivityManager>()
        if (manager == null) {
            close()
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(Unit)
            }
        }
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }
}
