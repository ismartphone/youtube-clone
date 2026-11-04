package com.youtubeclone.app.data.remote.dto

import com.youtubeclone.app.data.remote.resolveMediaUrl
import com.youtubeclone.app.domain.model.PlaylistDetail
import com.youtubeclone.app.domain.model.PlaylistSummary
import kotlinx.serialization.Serializable

// Réponses du module playlists du backend (playlists.controller.ts).

@Serializable
data class PlaylistSummaryDto(
    val id: String,
    val title: String,
    val videoCount: Int,
    val thumbnailUrl: String? = null,
    val containsVideo: Boolean = false,
)

@Serializable
data class PlaylistDetailDto(
    val id: String,
    val title: String,
    val videos: List<VideoSummaryDto>,
)

@Serializable
data class PlaylistTitleRequestDto(val title: String)

@Serializable
data class AddVideoRequestDto(val videoId: String)

fun PlaylistSummaryDto.toDomain() = PlaylistSummary(
    id = id,
    title = title,
    videoCount = videoCount,
    thumbnailUrl = thumbnailUrl?.let(::resolveMediaUrl),
    containsVideo = containsVideo,
)

fun PlaylistDetailDto.toDomain() = PlaylistDetail(
    id = id,
    title = title,
    videos = videos.map { it.toDomain() },
)
