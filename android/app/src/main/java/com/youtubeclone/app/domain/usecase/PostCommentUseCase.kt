package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.analytics.Analytics
import com.youtubeclone.app.domain.analytics.AnalyticsEvent
import com.youtubeclone.app.domain.model.Comment
import com.youtubeclone.app.domain.repository.CommentRepository
import javax.inject.Inject

class PostCommentUseCase @Inject constructor(
    private val repository: CommentRepository,
    private val analytics: Analytics,
) {
    suspend operator fun invoke(videoId: String, content: String): Comment =
        repository.postComment(videoId, content.trim()).also {
            analytics.track(AnalyticsEvent.CommentPosted(videoId))
        }

    companion object {
        /** Même limite que l'API. */
        const val MAX_LENGTH = 1000
    }
}
