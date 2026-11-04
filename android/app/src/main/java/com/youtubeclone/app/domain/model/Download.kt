package com.youtubeclone.app.domain.model

// Téléchargements pour la lecture hors ligne, réservés au Premium (ADR-019, partie 2).

/** Au plus 20 vidéos téléchargées. */
const val MAX_DOWNLOADS = 20

/** Une vidéo téléchargée doit être revalidée par le serveur au moins tous les 30 jours. */
const val OFFLINE_REVALIDATION_DAYS = 30L

enum class DownloadState { Queued, Downloading, Completed, Failed }

/**
 * Vidéo téléchargée ou en cours. [progress] : de 0 à 1 pendant le téléchargement. [playable] :
 * la lecture hors ligne est permise (téléchargement fini, Premium en cours, revalidée il y a
 * moins de 30 jours).
 */
data class DownloadedVideo(
    val videoId: String,
    val title: String,
    val channelName: String,
    val durationSeconds: Int,
    val thumbnailPath: String?,
    val subtitlePath: String?,
    val sizeBytes: Long,
    val state: DownloadState,
    val progress: Float,
    val playable: Boolean,
    /** Lecture refusée parce que la dernière revalidation date de plus de 30 jours. */
    val needsRevalidation: Boolean,
)

/** Refus d'un téléchargement avant tout envoi. */
sealed class DownloadProblem : Exception() {
    data object TooMany : DownloadProblem()
    data object NotEnoughSpace : DownloadProblem()
}
