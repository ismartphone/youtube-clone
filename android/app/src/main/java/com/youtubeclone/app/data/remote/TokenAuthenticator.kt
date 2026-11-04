package com.youtubeclone.app.data.remote

import com.youtubeclone.app.data.local.AuthTokens
import com.youtubeclone.app.data.local.TokenStore
import com.youtubeclone.app.data.remote.dto.RefreshRequestDto
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Appelé par OkHttp quand le serveur répond 401 : renouvelle les tokens via /auth/refresh,
 * puis rejoue la requête avec le nouvel access token.
 *
 * Le serveur n'accepte chaque refresh token qu'une fois : les renouvellements passent donc
 * un par un, et une requête qui attendait son tour réutilise le token déjà renouvelé.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    private val refreshApi: RefreshApi,
) : Authenticator {

    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        val request = response.request
        // Requête de visiteur, ou route de connexion : rien à renouveler.
        val failedToken = request.bearerToken() ?: return null
        if (request.isSessionRequest()) return null
        // Déjà rejouée une fois avec un nouveau token et toujours refusée : on abandonne.
        if (response.priorResponse != null) return null

        val accessToken = renew(failedToken) ?: return null
        return request.withBearer(accessToken)
    }

    /**
     * Renvoie un access token valide pour remplacer [rejectedAccessToken], refusé par le serveur,
     * ou null si la session ne peut pas être renouvelée. Sert aussi au WebSocket (fermeture 4401).
     * Appel bloquant : jamais depuis le thread principal.
     */
    fun renew(rejectedAccessToken: String): String? = synchronized(lock) {
        val current = runBlocking { tokenStore.get() } ?: return null
        if (current.accessToken != rejectedAccessToken) {
            // Une autre requête a renouvelé les tokens pendant qu'on attendait.
            return current.accessToken
        }
        refresh(current.refreshToken)?.accessToken
    }

    private fun refresh(refreshToken: String): AuthTokens? {
        val result = try {
            refreshApi.refresh(RefreshRequestDto(refreshToken)).execute()
        } catch (e: IOException) {
            // Serveur injoignable : la session est conservée, la requête échoue normalement.
            return null
        }
        val body = result.body()
        if (result.isSuccessful && body != null) {
            val renewed = AuthTokens(body.accessToken, body.refreshToken)
            // Enregistré pour son compte, sans le rendre actif : l'utilisateur a pu basculer entre-temps.
            runBlocking { tokenStore.save(renewed, activate = false) }
            return renewed
        }
        if (result.code() == 400 || result.code() == 401) {
            // Refresh token expiré, déjà utilisé ou invalide : la session est terminée.
            runBlocking { tokenStore.clear() }
        }
        return null
    }
}
