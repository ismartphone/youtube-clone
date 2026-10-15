package com.youtubeclone.app.data.remote

import com.youtubeclone.app.data.remote.dto.CommentDto
import com.youtubeclone.app.data.remote.dto.CommentPageDto
import com.youtubeclone.app.data.remote.dto.PostCommentRequestDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface CommentApi {
    // Ouverte aux visiteurs.
    @GET("videos/{id}/comments")
    suspend fun getComments(
        @Path("id") videoId: String,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
    ): CommentPageDto

    @POST("videos/{id}/comments")
    suspend fun postComment(@Path("id") videoId: String, @Body body: PostCommentRequestDto): CommentDto
}
