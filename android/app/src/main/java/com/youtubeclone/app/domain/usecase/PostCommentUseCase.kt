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
    /** [parentId] : commentaire auquel on répond (ADR-023), réponse comprise. */
    suspend operator fun invoke(videoId: String, content: String, parentId: String? = null): Comment =
        repository.postComment(videoId, content.trim(), parentId).also {
            analytics.track(AnalyticsEvent.CommentPosted(videoId))
        }

    companion object {
        /** Même limite que l'API. */
        const val MAX_LENGTH = 1000
    }
}
