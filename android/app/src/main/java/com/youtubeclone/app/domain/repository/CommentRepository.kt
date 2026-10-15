package com.youtubeclone.app.domain.repository

import com.youtubeclone.app.domain.model.Comment
import com.youtubeclone.app.domain.model.CommentPage

/** Commentaires d'une vidéo. Les implémentations lèvent une [com.youtubeclone.app.domain.model.DataError] en cas d'échec. */
interface CommentRepository {
    suspend fun getComments(videoId: String, page: Int, pageSize: Int): CommentPage

    /** Exige une session ouverte. Le commentaire est aussi diffusé en direct aux autres spectateurs. */
    suspend fun postComment(videoId: String, content: String): Comment
}
