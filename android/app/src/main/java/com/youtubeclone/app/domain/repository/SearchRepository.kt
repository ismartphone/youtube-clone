package com.youtubeclone.app.domain.repository

import com.youtubeclone.app.domain.model.SearchPage

/** Recherche dans les titres de vidéos et les noms de chaînes. Lève une [com.youtubeclone.app.domain.model.DataError] en cas d'échec. */
interface SearchRepository {
    suspend fun search(query: String, page: Int, pageSize: Int): SearchPage
}
