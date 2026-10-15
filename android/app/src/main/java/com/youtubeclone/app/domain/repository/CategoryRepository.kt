package com.youtubeclone.app.domain.repository

import com.youtubeclone.app.domain.model.Category

/** Catégories du catalogue. Les implémentations lèvent une [com.youtubeclone.app.domain.model.DataError] en cas d'échec. */
interface CategoryRepository {
    /** Triées par nom. */
    suspend fun getCategories(): List<Category>
}
