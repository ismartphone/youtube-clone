package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.model.CommentPage
import com.youtubeclone.app.domain.repository.CommentRepository
import javax.inject.Inject

class GetCommentsUseCase @Inject constructor(
    private val repository: CommentRepository,
) {
    suspend operator fun invoke(videoId: String, page: Int = 1, pageSize: Int = DEFAULT_PAGE_SIZE): CommentPage =
        repository.getComments(videoId, page, pageSize)

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}
