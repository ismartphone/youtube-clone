package com.youtubeclone.app.data.remote.dto

import kotlinx.serialization.Serializable

// Réponses des routes de téléchargement du backend (downloads.controller.ts).

@Serializable
data class DownloadGrantDto(
    val fileUrl: String,
    val subtitleUrl: String? = null,
    val sizeBytes: Long? = null,
    val expiresAt: String,
)

@Serializable
data class CheckDownloadsRequestDto(val videoIds: List<String>)

@Serializable
data class DownloadCheckDto(
    val expiresAt: String? = null,
    val accessible: List<String> = emptyList(),
)
