package com.youtubeclone.app.domain.model

import java.time.Instant

enum class VideoFormat { SHORT, LONG }

/** Qui voit la vidéo (ADR-016) : tous, ceux qui ont le lien, ou son propriétaire seulement. */
enum class VideoVisibility { PUBLIC, UNLISTED, PRIVATE }

data class Category(
    val id: String,
    val name: String,
)

data class Channel(
    val id: String,
    val name: String,
    val handle: String,
    val avatarUrl: String?,
)

data class Video(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val durationSeconds: Int,
    val format: VideoFormat,
    val isPremium: Boolean,
    val viewCount: Int,
    val publishedAt: Instant,
    val channel: Channel,
    val category: Category?,
    /** Une vidéo non publique n'arrive ici que pour son propriétaire (page de sa chaîne). */
    val visibility: VideoVisibility = VideoVisibility.PUBLIC,
    /** Commentaires désactivés : le bloc commentaires n'est pas affiché. */
    val commentsEnabled: Boolean = true,
)

/**
 * Détail d'une vidéo. [videoUrl] vaut null quand le serveur refuse l'accès au fichier,
 * par exemple pour une vidéo Premium : l'application ne décide jamais seule de l'accès.
 */
data class VideoDetail(
    val video: Video,
    val description: String?,
    val videoUrl: String?,
    /** Manifeste HLS multi-qualités (ADR-017) ; null sans HLS : le MP4 sert alors de repli. */
    val hlsUrl: String? = null,
    val likeStatus: LikeStatus,
)

data class VideoPage(
    val items: List<Video>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
)
