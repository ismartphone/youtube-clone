package com.youtubeclone.app.data.remote

import com.youtubeclone.app.data.remote.dto.LatestPostDto
import com.youtubeclone.app.data.remote.dto.PostDto
import com.youtubeclone.app.data.remote.dto.PostPageDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface PostApi {
    // Route protégée, réservée au propriétaire d'une chaîne (ADR-015). Sans image, la partie est omise.
    @Multipart
    @POST("me/posts")
    suspend fun createPost(
        @Part("text") text: RequestBody,
        @Part image: MultipartBody.Part?,
    ): PostDto

    // Ouverte aux visiteurs.
    @GET("channels/{id}/posts")
    suspend fun getChannelPosts(
        @Path("id") channelId: String,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
    ): PostPageDto

    // Post le plus récent de la plateforme, mis en avant dans le fil d'accueil. Ouverte aux visiteurs.
    @GET("posts/latest")
    suspend fun getLatestPost(): LatestPostDto

    // Posts des chaînes suivies, pour le fil Abonnements. Route protégée.
    @GET("me/feed/posts")
    suspend fun getFollowedPosts(
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
    ): PostPageDto
}
