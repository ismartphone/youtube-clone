package com.youtubeclone.app.domain.model

import java.time.Instant

data class CommentAuthor(
    val id: String,
    val displayName: String,
    val avatarUrl: String?,
)

data class Comment(
    val id: String,
    val videoId: String,
    val content: String,
    val createdAt: Instant,
    val author: CommentAuthor,
)

/** Une page de commentaires, du plus récent au plus ancien. [total] : nombre de commentaires de la vidéo. */
data class CommentPage(
    val items: List<Comment>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
)
