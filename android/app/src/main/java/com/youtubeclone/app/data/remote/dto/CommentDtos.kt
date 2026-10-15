package com.youtubeclone.app.data.remote.dto

import com.youtubeclone.app.domain.model.Comment
import com.youtubeclone.app.domain.model.CommentAuthor
import com.youtubeclone.app.domain.model.CommentPage
import java.time.Instant
import kotlinx.serialization.Serializable

// Réponses du module comments du backend (comment.presenter.ts). Même forme dans
// GET /videos/:id/comments, la réponse de POST et l'événement temps réel comment:new.

@Serializable
data class CommentAuthorDto(
    val id: String,
    val displayName: String,
    val avatarUrl: String? = null,
)

@Serializable
data class CommentDto(
    val id: String,
    val videoId: String,
    val content: String,
    val createdAt: String,
    val author: CommentAuthorDto,
)

@Serializable
data class CommentPageDto(
    val items: List<CommentDto>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
)

@Serializable
data class PostCommentRequestDto(
    val content: String,
)

fun CommentDto.toDomain() = Comment(
    id = id,
    videoId = videoId,
    content = content,
    createdAt = Instant.parse(createdAt),
    author = CommentAuthor(id = author.id, displayName = author.displayName, avatarUrl = author.avatarUrl),
)

fun CommentPageDto.toDomain() = CommentPage(
    items = items.map { it.toDomain() },
    page = page,
    pageSize = pageSize,
    total = total,
)
