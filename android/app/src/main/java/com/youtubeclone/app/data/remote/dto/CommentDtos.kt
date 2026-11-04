package com.youtubeclone.app.data.remote.dto

import com.youtubeclone.app.data.remote.resolveMediaUrl
import com.youtubeclone.app.domain.model.Comment
import com.youtubeclone.app.domain.model.CommentAuthor
import com.youtubeclone.app.domain.model.CommentPage
import com.youtubeclone.app.domain.model.CommentReaction
import com.youtubeclone.app.domain.model.CommentReactionStatus
import java.time.Instant
import kotlinx.serialization.Serializable

// Réponses du module comments du backend (comment.presenter.ts). Même forme dans les listes,
// la réponse de POST et les événements temps réel comment:new et comment:reply.

@Serializable
data class CommentAuthorDto(
    val id: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val handle: String? = null,
)

@Serializable
data class CommentDto(
    val id: String,
    val videoId: String,
    val content: String,
    val createdAt: String,
    val author: CommentAuthorDto,
    val parentId: String? = null,
    val likeCount: Int = 0,
    val replyCount: Int = 0,
    val heartedByCreator: Boolean = false,
    /** LIKE, DISLIKE, ou null. */
    val reaction: String? = null,
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
    val parentId: String? = null,
)

/** Réponse des routes /comments/:id/like et /dislike. */
@Serializable
data class CommentReactionDto(
    val likeCount: Int,
    val reaction: String? = null,
)

private fun reactionOf(value: String?): CommentReaction? = when (value) {
    "LIKE" -> CommentReaction.Like
    "DISLIKE" -> CommentReaction.Dislike
    else -> null
}

fun CommentDto.toDomain() = Comment(
    id = id,
    videoId = videoId,
    content = content,
    createdAt = Instant.parse(createdAt),
    author = CommentAuthor(
        id = author.id,
        displayName = author.displayName,
        avatarUrl = author.avatarUrl?.let(::resolveMediaUrl),
        handle = author.handle,
    ),
    parentId = parentId,
    likeCount = likeCount,
    replyCount = replyCount,
    heartedByCreator = heartedByCreator,
    reaction = reactionOf(reaction),
)

fun CommentPageDto.toDomain() = CommentPage(
    items = items.map { it.toDomain() },
    page = page,
    pageSize = pageSize,
    total = total,
)

fun CommentReactionDto.toDomain() = CommentReactionStatus(likeCount, reactionOf(reaction))
