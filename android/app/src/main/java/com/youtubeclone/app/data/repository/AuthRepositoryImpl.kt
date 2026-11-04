package com.youtubeclone.app.data.repository

import com.youtubeclone.app.data.local.AuthTokens
import com.youtubeclone.app.data.local.TokenStore
import com.youtubeclone.app.data.remote.AuthApi
import com.youtubeclone.app.data.remote.ChannelApi
import com.youtubeclone.app.data.remote.apiCall
import com.youtubeclone.app.data.remote.dto.LoginRequestDto
import com.youtubeclone.app.data.remote.dto.RegisterRequestDto
import com.youtubeclone.app.data.remote.dto.toDomain
import com.youtubeclone.app.data.remote.resolveMediaUrl
import com.youtubeclone.app.domain.model.Account
import com.youtubeclone.app.domain.model.DataError
import com.youtubeclone.app.domain.model.User
import com.youtubeclone.app.domain.repository.AuthRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class AuthRepositoryImpl @Inject constructor(
    private val api: AuthApi,
    private val channelApi: ChannelApi,
    private val tokenStore: TokenStore,
    private val json: Json,
) : AuthRepository {

    override val isLoggedIn: Flow<Boolean> =
        tokenStore.changes.map { it != null }.distinctUntilChanged()

    override val currentUserId: Flow<String?> = tokenStore.userId

    override val accounts: Flow<List<Account>> =
        combine(tokenStore.accounts, tokenStore.userId) { stored, activeId ->
            val now = System.currentTimeMillis()
            stored.map {
                Account(
                    userId = it.userId,
                    displayName = it.displayName,
                    email = it.email,
                    handle = it.handle,
                    avatarUrl = it.avatarUrl?.let(::resolveMediaUrl),
                    isActive = it.userId == activeId,
                    needsReconnection = it.userId != activeId && it.refreshExpiresAt <= now,
                )
            }
        }

    override suspend fun register(email: String, password: String, displayName: String) {
        apiCall(json) { api.register(RegisterRequestDto(email, password, displayName)) }
    }

    override suspend fun login(email: String, password: String) {
        val tokens = apiCall(json) { api.login(LoginRequestDto(email, password)) }
        tokenStore.save(AuthTokens(tokens.accessToken, tokens.refreshToken))
        // Sans réseau après la connexion, la feuille montrera le profil au prochain essai.
        runCatching { refreshActiveProfile() }.exceptionOrNull()?.let { if (it is CancellationException) throw it }
    }

    // Le backend n'a pas de route de déconnexion : on oublie le compte sur l'appareil.
    override suspend fun logout() {
        tokenStore.userId.first()?.let { tokenStore.remove(it) }
    }

    override suspend fun switchTo(userId: String): Boolean = tokenStore.activate(userId)

    override suspend fun removeAccount(userId: String) = tokenStore.remove(userId)

    override suspend fun refreshActiveProfile() {
        val userId = tokenStore.userId.first() ?: return
        val user = apiCall(json) { api.getMe() }
        val channel = apiCall(json) { channelApi.getOwnChannel() }.channel
        tokenStore.updateProfile(
            userId = userId,
            displayName = user.displayName,
            email = user.email,
            handle = channel?.handle,
            // Comme l'en-tête de Vous : la photo de la chaîne d'abord, sinon celle du compte.
            avatarUrl = channel?.avatarUrl ?: user.avatarUrl,
        )
    }

    override suspend fun getCurrentUser(): User =
        try {
            apiCall(json) { api.getMe() }.toDomain()
        } catch (e: DataError.NotFound) {
            // Token encore valide mais compte supprimé : la session n'a plus de sens.
            tokenStore.userId.first()?.let { tokenStore.remove(it) }
            throw e
        }
}
