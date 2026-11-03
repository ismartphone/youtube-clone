package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.model.SearchPage
import com.youtubeclone.app.domain.repository.SearchRepository
import javax.inject.Inject

/** Cherche [query] dans les titres de vidéos et les noms de chaînes, insensible à la casse et aux accents. */
class SearchUseCase @Inject constructor(
    private val repository: SearchRepository,
) {
    suspend operator fun invoke(query: String, page: Int = 1, pageSize: Int = PAGE_SIZE): SearchPage =
        repository.search(query.trim(), page, pageSize)

    companion object {
        const val PAGE_SIZE = 20

        /** Même limite que l'API. */
        const val MAX_LENGTH = 100
    }
}
