package com.youtubeclone.app.domain.repository

import com.youtubeclone.app.domain.model.PlaylistDetail
import com.youtubeclone.app.domain.model.PlaylistSummary
import kotlinx.coroutines.flow.Flow

/**
 * Playlists de l'utilisateur connecté (ADR-019). Exige une session ouverte. Les implémentations
 * lèvent une [com.youtubeclone.app.domain.model.DataError] en cas d'échec.
 */
interface PlaylistRepository {
    /** Émet après chaque modification réussie, quel que soit l'écran qui l'a faite. */
    val changes: Flow<Unit>

    /** Avec [videoId], chaque playlist indique si elle contient cette vidéo. */
    suspend fun getPlaylists(videoId: String? = null): List<PlaylistSummary>
    suspend fun createPlaylist(title: String): PlaylistSummary
    suspend fun getPlaylist(id: String): PlaylistDetail
    suspend fun renamePlaylist(id: String, title: String): PlaylistDetail
    suspend fun deletePlaylist(id: String)

    /** Ajoute (true) ou retire (false) la vidéo de la playlist. */
    suspend fun setVideoInPlaylist(playlistId: String, videoId: String, inPlaylist: Boolean)
}
