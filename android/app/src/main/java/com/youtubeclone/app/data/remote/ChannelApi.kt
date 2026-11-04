package com.youtubeclone.app.data.remote

import com.youtubeclone.app.data.remote.dto.ChannelPageDto
import com.youtubeclone.app.data.remote.dto.ChannelProfileDto
import com.youtubeclone.app.data.remote.dto.ChannelSummaryDto
import com.youtubeclone.app.data.remote.dto.CreateChannelRequestDto
import com.youtubeclone.app.data.remote.dto.FollowStatusDto
import com.youtubeclone.app.data.remote.dto.OwnChannelDto
import com.youtubeclone.app.data.remote.dto.UpdateChannelRequestDto
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path

interface ChannelApi {
    // Ouverte aux visiteurs. Avec une session, la réponse indique si l'utilisateur est abonné.
    @GET("channels/{id}")
    suspend fun getChannel(@Path("id") id: String): ChannelPageDto

    // Chaîne de l'utilisateur connecté ; channel vaut null s'il n'en a pas créé.
    // Route protégée : crée la chaîne de l'utilisateur connecté.
    @POST("channels")
    suspend fun createChannel(@Body body: CreateChannelRequestDto): ChannelSummaryDto

    @GET("me/channel")
    suspend fun getOwnChannel(): OwnChannelDto

    // Personnalisation de sa propre chaîne (ADR-018) : routes protégées, sans identifiant.
    @PATCH("me/channel")
    suspend fun updateOwnChannel(@Body body: UpdateChannelRequestDto): ChannelProfileDto

    @Multipart
    @PUT("me/channel/photo")
    suspend fun setPhoto(@Part image: MultipartBody.Part): ChannelProfileDto

    @DELETE("me/channel/photo")
    suspend fun removePhoto(): ChannelProfileDto

    @Multipart
    @PUT("me/channel/banner")
    suspend fun setBanner(@Part image: MultipartBody.Part): ChannelProfileDto

    @DELETE("me/channel/banner")
    suspend fun removeBanner(): ChannelProfileDto

    @POST("channels/{id}/follow")
    suspend fun follow(@Path("id") id: String): FollowStatusDto

    @DELETE("channels/{id}/follow")
    suspend fun unfollow(@Path("id") id: String): FollowStatusDto
}
