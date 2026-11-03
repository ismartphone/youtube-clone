package com.youtubeclone.app.data.remote

import com.youtubeclone.app.data.remote.dto.SearchResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface SearchApi {
    // Route publique : un visiteur peut chercher.
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
    ): SearchResponseDto
}
