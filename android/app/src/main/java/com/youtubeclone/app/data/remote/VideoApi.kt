package com.youtubeclone.app.data.remote

import com.youtubeclone.app.data.remote.dto.VideoDetailDto
import com.youtubeclone.app.data.remote.dto.VideoPageDto
import retrofit2.http.GET
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
}
