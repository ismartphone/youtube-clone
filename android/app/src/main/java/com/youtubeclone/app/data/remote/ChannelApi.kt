package com.youtubeclone.app.data.remote

import com.youtubeclone.app.data.remote.dto.ChannelPageDto
import com.youtubeclone.app.data.remote.dto.FollowStatusDto
import com.youtubeclone.app.data.remote.dto.OwnChannelDto
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ChannelApi {
    // Ouverte aux visiteurs. Avec une session, la réponse indique si l'utilisateur est abonné.
    @GET("channels/{id}")
    suspend fun getChannel(@Path("id") id: String): ChannelPageDto

    // Chaîne de l'utilisateur connecté ; channel vaut null s'il n'en a pas créé.
    @GET("me/channel")
    suspend fun getOwnChannel(): OwnChannelDto

    @POST("channels/{id}/follow")
    suspend fun follow(@Path("id") id: String): FollowStatusDto

    @DELETE("channels/{id}/follow")
    suspend fun unfollow(@Path("id") id: String): FollowStatusDto
}
