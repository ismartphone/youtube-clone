package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.model.Category
import com.youtubeclone.app.domain.repository.CategoryRepository
import javax.inject.Inject

class GetCategoriesUseCase @Inject constructor(
    private val repository: CategoryRepository,
) {
    suspend operator fun invoke(): List<Category> = repository.getCategories()
}
