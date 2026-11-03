package com.youtubeclone.app.data.repository

import com.youtubeclone.app.data.remote.PremiumApi
import com.youtubeclone.app.data.remote.apiCall
import com.youtubeclone.app.data.remote.dto.SubscribeRequest
import com.youtubeclone.app.data.remote.dto.toDomain
import com.youtubeclone.app.domain.model.PremiumStatus
import com.youtubeclone.app.domain.repository.AuthRepository
import com.youtubeclone.app.domain.repository.PremiumRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Instance unique (voir RepositoryModule) : tous les écrans partagent le même état Premium.
 * Il suit la session : remis à inactif à la déconnexion, relu à chaque connexion.
 */
class PremiumRepositoryImpl @Inject constructor(
    private val api: PremiumApi,
    private val json: Json,
    private val auth: AuthRepository,
) : PremiumRepository {

    private val _status = MutableStateFlow<PremiumStatus?>(null)
    override val status: StateFlow<PremiumStatus?> = _status.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            auth.isLoggedIn.distinctUntilChanged().collect { loggedIn ->
                if (loggedIn) {
                    _status.value = null
                    try {
                        refresh()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Serveur injoignable : l'état reste inconnu, les écrans le relisent au besoin.
                    }
                } else {
                    _status.value = PremiumStatus.Inactive
                }
            }
        }
    }

    override suspend fun refresh(): PremiumStatus {
        if (!auth.isLoggedIn.first()) return PremiumStatus.Inactive.also { _status.value = it }
        return apiCall(json) { api.getStatus() }.toDomain().also { _status.value = it }
    }

    override suspend fun startCheckout(): String =
        apiCall(json) { api.checkout() }.purchaseToken

    override suspend fun confirmPurchase(purchaseToken: String): PremiumStatus =
        apiCall(json) { api.subscribe(SubscribeRequest(purchaseToken)) }.toDomain()
            .also { _status.value = it }

    override suspend fun cancel(): PremiumStatus =
        apiCall(json) { api.cancel() }.toDomain().also { _status.value = it }
}
