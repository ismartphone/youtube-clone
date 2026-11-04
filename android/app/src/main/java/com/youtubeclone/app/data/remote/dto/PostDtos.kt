package com.youtubeclone.app.data.remote.dto

import com.youtubeclone.app.data.remote.resolveMediaUrl
import com.youtubeclone.app.domain.model.Post
import com.youtubeclone.app.domain.model.PostPage
import java.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class PostDto(
    val id: String,
    val text: String,
    val imageUrl: String? = null,
    val createdAt: String,
    val channel: ChannelSummaryDto,
)

@Serializable
data class PostPageDto(
    val items: List<PostDto>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
)

/** Réponse de GET /posts/latest : null s'il n'y a aucun post. */
@Serializable
data class LatestPostDto(
    val post: PostDto? = null,
)

fun PostDto.toDomain() = Post(
    id = id,
    text = text,
    // Image hébergée chez nous : chemin relatif, complété comme les miniatures.
    imageUrl = imageUrl?.let(::resolveMediaUrl),
    createdAt = Instant.parse(createdAt),
    channel = channel.toDomain(),
)

fun PostPageDto.toDomain() = PostPage(
    items = items.map { it.toDomain() },
    page = page,
    pageSize = pageSize,
    total = total,
)
