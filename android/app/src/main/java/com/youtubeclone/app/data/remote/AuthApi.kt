package com.youtubeclone.app.data.remote

import com.youtubeclone.app.data.remote.dto.LoginRequestDto
import com.youtubeclone.app.data.remote.dto.RefreshRequestDto
import com.youtubeclone.app.data.remote.dto.RegisterRequestDto
import com.youtubeclone.app.data.remote.dto.TokenPairDto
import com.youtubeclone.app.data.remote.dto.UserDto
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequestDto): UserDto

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): TokenPairDto

    @GET("auth/me")
    suspend fun getMe(): UserDto
}

/**
 * Renouvellement des tokens, appelé de façon synchrone par [TokenAuthenticator].
 * Il passe par un client OkHttp sans intercepteur ni authenticator, pour ne jamais se rappeler lui-même.
 */
interface RefreshApi {
    @POST("auth/refresh")
    fun refresh(@Body body: RefreshRequestDto): Call<TokenPairDto>
}
