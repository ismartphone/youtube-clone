package com.youtubeclone.app.domain.model

/**
 * Publicité vidéo (ADR-021), tirée au sort par le serveur : jamais pour un compte Premium ni
 * avant un Short. [streamUrl] : manifeste HLS quand il est prêt ([isHls]), MP4 sinon.
 */
data class VideoAd(
    val id: String,
    val title: String,
    val streamUrl: String,
    val isHls: Boolean,
    val thumbnailUrl: String?,
    val durationSeconds: Int,
)
