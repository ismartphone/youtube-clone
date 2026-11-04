package com.youtubeclone.app.data.remote.dto

import com.youtubeclone.app.data.remote.resolveMediaUrl
import com.youtubeclone.app.domain.model.VideoAd
import kotlinx.serialization.Serializable

// Publicité renvoyée par le module ads du backend (ad.presenter.ts, ADR-021).

@Serializable
data class AdDto(
    val id: String,
    val title: String,
    val videoUrl: String,
    // Null tant que l'échelle HLS n'est pas générée : le MP4 est lu en attendant.
    val hlsUrl: String? = null,
    val thumbnailUrl: String? = null,
    val durationSeconds: Int,
)

fun AdDto.toDomain() = VideoAd(
    id = id,
    title = title,
    streamUrl = resolveMediaUrl(hlsUrl ?: videoUrl),
    isHls = hlsUrl != null,
    thumbnailUrl = thumbnailUrl?.let(::resolveMediaUrl),
    durationSeconds = durationSeconds,
)
