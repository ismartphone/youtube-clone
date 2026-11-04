package com.youtubeclone.app.domain.repository

import com.youtubeclone.app.domain.model.Comment
import com.youtubeclone.app.domain.model.CommentPage
import com.youtubeclone.app.domain.model.CommentReaction
import com.youtubeclone.app.domain.model.CommentReactionStatus
import com.youtubeclone.app.domain.model.CommentSort

/** Commentaires d'une vidéo. Les implémentations lèvent une [com.youtubeclone.app.domain.model.DataError] en cas d'échec. */
interface CommentRepository {
    /** Commentaires de premier niveau, chacun avec son nombre de réponses. */
    suspend fun getComments(videoId: String, page: Int, pageSize: Int, sort: CommentSort): CommentPage

    /** Réponses d'un commentaire, 10 par page, de la plus ancienne à la plus récente (ADR-023). */
    suspend fun getReplies(commentId: String, page: Int): CommentPage

    /**
     * Exige une session ouverte. [parentId] : commentaire auquel on répond, réponse comprise ; le
     * serveur range la réponse sous le commentaire de premier niveau. Diffusé en direct.
     */
    suspend fun postComment(videoId: String, content: String, parentId: String? = null): Comment

    /** Pose ([on]) ou retire un avis. Exige une session ouverte. */
    suspend fun react(commentId: String, reaction: CommentReaction, on: Boolean): CommentReactionStatus

    /** Cœur du créateur, réservé au propriétaire de la chaîne de la vidéo. */
    suspend fun setHeart(commentId: String, hearted: Boolean)
}
