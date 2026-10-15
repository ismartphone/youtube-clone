package com.youtubeclone.app.data.repository

import com.youtubeclone.app.data.remote.CategoryApi
import com.youtubeclone.app.data.remote.apiCall
import com.youtubeclone.app.data.remote.dto.toDomain
import com.youtubeclone.app.domain.model.Category
import com.youtubeclone.app.domain.repository.CategoryRepository
import javax.inject.Inject
import kotlinx.serialization.json.Json

class CategoryRepositoryImpl @Inject constructor(
    private val api: CategoryApi,
    private val json: Json,
) : CategoryRepository {

    override suspend fun getCategories(): List<Category> =
        apiCall(json) { api.getCategories() }.map { it.toDomain() }
}
