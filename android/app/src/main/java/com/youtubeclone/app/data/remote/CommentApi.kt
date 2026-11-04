package com.youtubeclone.app.data.remote

import com.youtubeclone.app.data.remote.dto.CommentDto
import com.youtubeclone.app.data.remote.dto.CommentPageDto
import com.youtubeclone.app.data.remote.dto.CommentReactionDto
import com.youtubeclone.app.data.remote.dto.PostCommentRequestDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface CommentApi {
    // Ouverte aux visiteurs. sort : TOP (par défaut) ou NEWEST.
    @GET("videos/{id}/comments")
    suspend fun getComments(
        @Path("id") videoId: String,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
        @Query("sort") sort: String,
    ): CommentPageDto

    @POST("videos/{id}/comments")
    suspend fun postComment(@Path("id") videoId: String, @Body body: PostCommentRequestDto): CommentDto

    // Ouverte aux visiteurs (ADR-023).
    @GET("comments/{id}/replies")
    suspend fun getReplies(@Path("id") commentId: String, @Query("page") page: Int): CommentPageDto

    @POST("comments/{id}/like")
    suspend fun like(@Path("id") commentId: String): CommentReactionDto

    @DELETE("comments/{id}/like")
    suspend fun unlike(@Path("id") commentId: String): CommentReactionDto

    @POST("comments/{id}/dislike")
    suspend fun dislike(@Path("id") commentId: String): CommentReactionDto

    @DELETE("comments/{id}/dislike")
    suspend fun undislike(@Path("id") commentId: String): CommentReactionDto

    // Réservées au propriétaire de la chaîne ; réponse vide (204).
    @PUT("comments/{id}/heart")
    suspend fun heart(@Path("id") commentId: String)

    @DELETE("comments/{id}/heart")
    suspend fun unheart(@Path("id") commentId: String)
}
