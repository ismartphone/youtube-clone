package com.youtubeclone.app.data.repository

import com.youtubeclone.app.data.remote.SearchApi
import com.youtubeclone.app.data.remote.apiCall
import com.youtubeclone.app.data.remote.dto.toDomain
import com.youtubeclone.app.domain.model.SearchPage
import com.youtubeclone.app.domain.repository.SearchRepository
import javax.inject.Inject
import kotlinx.serialization.json.Json

class SearchRepositoryImpl @Inject constructor(
    private val api: SearchApi,
    private val json: Json,
) : SearchRepository {

    override suspend fun search(query: String, page: Int, pageSize: Int): SearchPage =
        apiCall(json) { api.search(query, page, pageSize) }.toDomain()
}
