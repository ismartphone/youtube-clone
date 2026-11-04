package com.youtubeclone.app.data.remote

import com.youtubeclone.app.data.remote.dto.DeviceTokenDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.Path

/** Jeton FCM de l'appareil (ADR-020) : routes protégées, au nom de l'utilisateur connecté. */
interface DeviceApi {
    @POST("me/devices")
    suspend fun register(@Body body: DeviceTokenDto)

    @DELETE("me/devices/{token}")
    suspend fun unregister(@Path("token") token: String)
}
