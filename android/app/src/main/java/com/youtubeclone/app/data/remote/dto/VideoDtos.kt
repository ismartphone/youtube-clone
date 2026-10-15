package com.youtubeclone.app.data.remote.dto

import kotlinx.serialization.Serializable

// Modèles de réponse de l'API, calqués sur les présentateurs du backend (module videos).

@Serializable
data class ChannelSummaryDto(
    val id: String,
    val name: String,
    val handle: String,
    val avatarUrl: String? = null,
)

@Serializable
data class CategoryDto(
    val id: String,
    val name: String,
)

@Serializable
data class VideoSummaryDto(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val durationSeconds: Int,
    val format: String,
    val isPremium: Boolean,
    val viewCount: Int,
    val publishedAt: String,
    val channel: ChannelSummaryDto,
    val category: CategoryDto? = null,
)

@Serializable
data class VideoDetailDto(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val durationSeconds: Int,
    val format: String,
    val isPremium: Boolean,
    val viewCount: Int,
    val publishedAt: String,
    val channel: ChannelSummaryDto,
    val category: CategoryDto? = null,
    val description: String? = null,
    // Null quand le serveur refuse l'accès au fichier (vidéo Premium).
    val videoUrl: String? = null,
)

@Serializable
data class VideoPageDto(
    val items: List<VideoSummaryDto>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
)
