package com.youtubeclone.app.data.repository

import com.youtubeclone.app.data.remote.CommentApi
import com.youtubeclone.app.data.remote.apiCall
import com.youtubeclone.app.data.remote.dto.PostCommentRequestDto
import com.youtubeclone.app.data.remote.dto.toDomain
import com.youtubeclone.app.domain.model.Comment
import com.youtubeclone.app.domain.model.CommentPage
import com.youtubeclone.app.domain.model.CommentReaction
import com.youtubeclone.app.domain.model.CommentReactionStatus
import com.youtubeclone.app.domain.model.CommentSort
import com.youtubeclone.app.domain.repository.CommentRepository
import javax.inject.Inject
import kotlinx.serialization.json.Json

class CommentRepositoryImpl @Inject constructor(
    private val api: CommentApi,
    private val json: Json,
) : CommentRepository {

    override suspend fun getComments(videoId: String, page: Int, pageSize: Int, sort: CommentSort): CommentPage {
        val sortParam = if (sort == CommentSort.Top) "TOP" else "NEWEST"
        return apiCall(json) { api.getComments(videoId, page, pageSize, sortParam) }.toDomain()
    }

    override suspend fun getReplies(commentId: String, page: Int): CommentPage =
        apiCall(json) { api.getReplies(commentId, page) }.toDomain()

    // apiCall garde le message du serveur (par exemple un refus de validation) pour l'afficher.
    override suspend fun postComment(videoId: String, content: String, parentId: String?): Comment =
        apiCall(json) { api.postComment(videoId, PostCommentRequestDto(content, parentId)) }.toDomain()

    override suspend fun react(commentId: String, reaction: CommentReaction, on: Boolean): CommentReactionStatus =
        apiCall(json) {
            when (reaction) {
                CommentReaction.Like -> if (on) api.like(commentId) else api.unlike(commentId)
                CommentReaction.Dislike -> if (on) api.dislike(commentId) else api.undislike(commentId)
            }
        }.toDomain()

    override suspend fun setHeart(commentId: String, hearted: Boolean) =
        apiCall(json) { if (hearted) api.heart(commentId) else api.unheart(commentId) }
}
