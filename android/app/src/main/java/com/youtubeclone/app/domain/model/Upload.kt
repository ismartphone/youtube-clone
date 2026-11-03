package com.youtubeclone.app.domain.model

// Publication d'une vidéo depuis l'application (ADR-015). Les mêmes règles sont vérifiées par
// le serveur avec ffprobe : celles-ci ne servent qu'à refuser tout de suite un fichier qui ne
// convient pas, sans l'envoyer.

/** 200 Mo par vidéo, comme le serveur. */
const val MAX_UPLOAD_BYTES = 200L * 1024 * 1024

/** Durée maximale d'un Short. */
const val SHORT_MAX_SECONDS = 60

// Demi-seconde de marge, comme le serveur : les enregistreurs produisent souvent 60,02 s.
private const val SHORT_TOLERANCE_MS = 500L

/** Vidéo choisie dans la galerie, telle qu'elle s'affiche (rotation appliquée). */
data class PickedVideo(
    val uri: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
)

/** Raison pour laquelle une vidéo choisie ne peut pas être publiée dans le format demandé. */
enum class PickedVideoProblem { Unreadable, TooLarge, NotVertical, TooLong }

fun PickedVideo.problemFor(format: VideoFormat): PickedVideoProblem? = when {
    sizeBytes > MAX_UPLOAD_BYTES -> PickedVideoProblem.TooLarge
    format == VideoFormat.SHORT && height <= width -> PickedVideoProblem.NotVertical
    format == VideoFormat.SHORT && durationMs > SHORT_MAX_SECONDS * 1000L + SHORT_TOLERANCE_MS ->
        PickedVideoProblem.TooLong
    else -> null
}

/** Ce que le créateur a saisi sur l'écran de publication. */
data class VideoUpload(
    val uri: String,
    val format: VideoFormat,
    val title: String,
    val description: String?,
    val categoryId: String,
)

/** Avancement d'un envoi : octets envoyés, puis vidéo publiée. */
sealed interface UploadProgress {
    data class Sending(val sentBytes: Long, val totalBytes: Long) : UploadProgress
    data class Published(val video: Video) : UploadProgress
}
