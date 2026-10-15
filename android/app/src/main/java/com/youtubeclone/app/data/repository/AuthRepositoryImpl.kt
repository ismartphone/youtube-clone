package com.youtubeclone.app.data.repository

import com.youtubeclone.app.data.local.AuthTokens
import com.youtubeclone.app.data.local.TokenStore
import com.youtubeclone.app.data.remote.AuthApi
import com.youtubeclone.app.data.remote.apiCall
import com.youtubeclone.app.data.remote.dto.LoginRequestDto
import com.youtubeclone.app.data.remote.dto.RegisterRequestDto
import com.youtubeclone.app.data.remote.dto.toDomain
import com.youtubeclone.app.domain.model.DataError
import com.youtubeclone.app.domain.model.User
import com.youtubeclone.app.domain.repository.AuthRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class AuthRepositoryImpl @Inject constructor(
    private val api: AuthApi,
    private val tokenStore: TokenStore,
    private val json: Json,
) : AuthRepository {

    override val isLoggedIn: Flow<Boolean> =
        tokenStore.changes.map { it != null }.distinctUntilChanged()

    override suspend fun register(email: String, password: String, displayName: String) {
        apiCall(json) { api.register(RegisterRequestDto(email, password, displayName)) }
    }

    override suspend fun login(email: String, password: String) {
        val tokens = apiCall(json) { api.login(LoginRequestDto(email, password)) }
        tokenStore.save(AuthTokens(tokens.accessToken, tokens.refreshToken))
    }

    // Le backend n'a pas de route de déconnexion : on oublie les tokens sur l'appareil.
    override suspend fun logout() = tokenStore.clear()

    override suspend fun getCurrentUser(): User =
        try {
            apiCall(json) { api.getMe() }.toDomain()
        } catch (e: DataError.NotFound) {
            // Token encore valide mais compte supprimé : la session n'a plus de sens.
            tokenStore.clear()
            throw e
        }
}
