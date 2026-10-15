package com.youtubeclone.app.data.remote

import com.youtubeclone.app.data.local.TokenStore
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Ajoute l'access token aux requêtes vers l'API quand une session est ouverte.
 * Les routes publiques l'ignorent ; les autres (vidéos Premium, actions du compte) en ont besoin.
 * Hors session, les requêtes partent sans en-tête : l'application reste utilisable en visiteur.
 */
class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(AUTHORIZATION) != null || request.isSessionRequest()) {
            return chain.proceed(request)
        }
        // Les appels OkHttp ne tournent jamais sur le thread principal : l'attente est sans risque.
        val accessToken = runBlocking { tokenStore.get() }?.accessToken
            ?: return chain.proceed(request)
        return chain.proceed(request.withBearer(accessToken))
    }
}

private const val AUTHORIZATION = "Authorization"
private const val BEARER_PREFIX = "Bearer "

// Routes qui ouvrent ou renouvellent une session : un 401 y signifie « identifiants refusés »,
// jamais « access token expiré ».
private val SESSION_PATHS = listOf("auth/login", "auth/register", "auth/refresh")

internal fun Request.isSessionRequest(): Boolean =
    SESSION_PATHS.any { url.encodedPath.endsWith(it) }

internal fun Request.withBearer(accessToken: String): Request =
    newBuilder().header(AUTHORIZATION, BEARER_PREFIX + accessToken).build()

internal fun Request.bearerToken(): String? =
    header(AUTHORIZATION)?.takeIf { it.startsWith(BEARER_PREFIX) }?.removePrefix(BEARER_PREFIX)
