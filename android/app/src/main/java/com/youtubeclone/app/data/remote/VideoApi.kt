package com.youtubeclone.app.data.remote

import com.youtubeclone.app.data.remote.dto.LikeStatusDto
import com.youtubeclone.app.data.remote.dto.SponsoredAdDto
import com.youtubeclone.app.data.remote.dto.VideoDetailDto
import com.youtubeclone.app.data.remote.dto.VideoPageDto
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface VideoApi {
    @GET("videos")
    suspend fun getVideos(
        @Query("format") format: String?,
        @Query("categoryId") categoryId: String?,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
    ): VideoPageDto

    @GET("videos/{id}")
    suspend fun getVideo(@Path("id") id: String): VideoDetailDto

    // Bandeau sponsorisé du fil (ADR-021), décidé par le serveur selon le statut Premium.
    @GET("ads/sponsored")
    suspend fun getSponsoredAd(): SponsoredAdDto

    // Réponse 204 sans contenu.
    @POST("videos/{id}/view")
    suspend fun recordView(@Path("id") id: String)

    @POST("videos/{id}/like")
    suspend fun like(@Path("id") id: String): LikeStatusDto

    @DELETE("videos/{id}/like")
    suspend fun unlike(@Path("id") id: String): LikeStatusDto

    // « Je n'aime pas » (ADR-019) : retire le « j'aime » éventuel ; aucun nombre n'est renvoyé.
    @POST("videos/{id}/dislike")
    suspend fun dislike(@Path("id") id: String): LikeStatusDto

    @DELETE("videos/{id}/dislike")
    suspend fun undislike(@Path("id") id: String): LikeStatusDto

    // Route protégée : l'access token est ajouté par AuthInterceptor.
    @GET("me/feed")
    suspend fun getSubscriptionFeed(
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
    ): VideoPageDto

    // Routes protégées : de la vidéo regardée, ou aimée, le plus récemment à la plus ancienne.
    @GET("me/history")
    suspend fun getWatchHistory(
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
    ): VideoPageDto

    @GET("me/liked")
    suspend fun getLikedVideos(
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
    ): VideoPageDto
}
