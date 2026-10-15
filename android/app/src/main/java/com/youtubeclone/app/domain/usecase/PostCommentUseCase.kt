package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.model.Comment
import com.youtubeclone.app.domain.repository.CommentRepository
import javax.inject.Inject

class PostCommentUseCase @Inject constructor(
    private val repository: CommentRepository,
) {
    suspend operator fun invoke(videoId: String, content: String): Comment =
        repository.postComment(videoId, content.trim())

    companion object {
        /** Même limite que l'API. */
        const val MAX_LENGTH = 1000
    }
}
