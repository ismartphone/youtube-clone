package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.model.CommentPage
import com.youtubeclone.app.domain.model.CommentReaction
import com.youtubeclone.app.domain.model.CommentReactionStatus
import com.youtubeclone.app.domain.repository.CommentRepository
import javax.inject.Inject

/** Réponses d'un commentaire, 10 par page, de la plus ancienne à la plus récente (ADR-023). */
class GetRepliesUseCase @Inject constructor(
    private val repository: CommentRepository,
) {
    suspend operator fun invoke(commentId: String, page: Int = 1): CommentPage = repository.getReplies(commentId, page)
}

/** « J'aime » ou « Je n'aime pas » sur un commentaire ou une réponse ; [on] pose ou retire l'avis. */
class ReactToCommentUseCase @Inject constructor(
    private val repository: CommentRepository,
) {
    suspend operator fun invoke(commentId: String, reaction: CommentReaction, on: Boolean): CommentReactionStatus =
        repository.react(commentId, reaction, on)
}

/** Cœur du créateur, réservé au propriétaire de la chaîne de la vidéo. */
class HeartCommentUseCase @Inject constructor(
    private val repository: CommentRepository,
) {
    suspend operator fun invoke(commentId: String, hearted: Boolean) = repository.setHeart(commentId, hearted)
}

/** Cas d'usage des commentaires, réunis pour le contrôleur partagé par la lecture et les Shorts. */
class CommentUseCases @Inject constructor(
    val getComments: GetCommentsUseCase,
    val getReplies: GetRepliesUseCase,
    val postComment: PostCommentUseCase,
    val react: ReactToCommentUseCase,
    val heart: HeartCommentUseCase,
)
