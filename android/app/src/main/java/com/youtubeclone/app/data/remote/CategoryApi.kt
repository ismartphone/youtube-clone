package com.youtubeclone.app.data.remote

import com.youtubeclone.app.data.remote.dto.CategoryDto
import retrofit2.http.GET

interface CategoryApi {
    // Ouverte aux visiteurs.
    @GET("categories")
    suspend fun getCategories(): List<CategoryDto>
}
