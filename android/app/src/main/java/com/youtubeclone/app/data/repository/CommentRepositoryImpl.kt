package com.youtubeclone.app.data.repository

import com.youtubeclone.app.data.remote.CommentApi
import com.youtubeclone.app.data.remote.apiCall
import com.youtubeclone.app.data.remote.dto.PostCommentRequestDto
import com.youtubeclone.app.data.remote.dto.toDomain
import com.youtubeclone.app.domain.model.Comment
import com.youtubeclone.app.domain.model.CommentPage
import com.youtubeclone.app.domain.repository.CommentRepository
import javax.inject.Inject
import kotlinx.serialization.json.Json

class CommentRepositoryImpl @Inject constructor(
    private val api: CommentApi,
    private val json: Json,
) : CommentRepository {

    override suspend fun getComments(videoId: String, page: Int, pageSize: Int): CommentPage =
        apiCall(json) { api.getComments(videoId, page, pageSize) }.toDomain()

    // apiCall garde le message du serveur (par exemple un refus de validation) pour l'afficher.
    override suspend fun postComment(videoId: String, content: String): Comment =
        apiCall(json) { api.postComment(videoId, PostCommentRequestDto(content)) }.toDomain()
}
