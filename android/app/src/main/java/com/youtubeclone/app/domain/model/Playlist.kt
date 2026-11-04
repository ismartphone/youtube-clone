package com.youtubeclone.app.domain.model

// Playlists de l'utilisateur (ADR-019, partie 3) : privées, vidéos dans l'ordre d'ajout.

/** Titre de 100 caractères au plus, comme le serveur. */
const val PLAYLIST_TITLE_MAX_LENGTH = 100

/**
 * Une playlist dans la liste de l'utilisateur. [thumbnailUrl] : miniature de sa première vidéo,
 * null si elle est vide. [containsVideo] : renseigné quand la liste est lue pour une vidéo précise.
 */
data class PlaylistSummary(
    val id: String,
    val title: String,
    val videoCount: Int,
    val thumbnailUrl: String?,
    val containsVideo: Boolean = false,
)

/** Contenu d'une playlist, de la vidéo ajoutée la première à la dernière. */
data class PlaylistDetail(
    val id: String,
    val title: String,
    val videos: List<Video>,
)
