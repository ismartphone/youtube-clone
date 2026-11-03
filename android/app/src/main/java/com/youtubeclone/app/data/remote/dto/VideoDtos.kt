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
    val visibility: String = "PUBLIC",
    val commentsEnabled: Boolean = true,
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
    val visibility: String = "PUBLIC",
    val commentsEnabled: Boolean = true,
    val description: String? = null,
    // Null quand le serveur refuse l'accès au fichier (vidéo Premium).
    val videoUrl: String? = null,
    // Null sans HLS (vidéo externe, génération pas encore faite) ou accès refusé.
    val hlsUrl: String? = null,
    // Null sans sous-titres (vidéo externe, transcription pas faite ou échouée) ou accès refusé.
    val subtitleUrl: String? = null,
    val likeCount: Int = 0,
    // Vrai seulement si la requête portait le token d'un utilisateur qui a aimé la vidéo.
    val isLiked: Boolean = false,
)

@Serializable
data class LikeStatusDto(
    val likeCount: Int,
    val isLiked: Boolean,
)

@Serializable
data class VideoPageDto(
    val items: List<VideoSummaryDto>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
)
