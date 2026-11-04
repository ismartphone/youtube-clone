package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.model.PlaylistDetail
import com.youtubeclone.app.domain.model.PlaylistSummary
import com.youtubeclone.app.domain.repository.PlaylistRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

// Playlists de l'utilisateur (ADR-019, partie 3).

class GetPlaylistsUseCase @Inject constructor(private val repository: PlaylistRepository) {
    /** Avec [videoId] : la feuille « Enregistrer » coche les playlists qui la contiennent. */
    suspend operator fun invoke(videoId: String? = null): List<PlaylistSummary> = repository.getPlaylists(videoId)
}

class GetPlaylistUseCase @Inject constructor(private val repository: PlaylistRepository) {
    suspend operator fun invoke(id: String): PlaylistDetail = repository.getPlaylist(id)
}

class CreatePlaylistUseCase @Inject constructor(private val repository: PlaylistRepository) {
    suspend operator fun invoke(title: String): PlaylistSummary = repository.createPlaylist(title.trim())
}

class RenamePlaylistUseCase @Inject constructor(private val repository: PlaylistRepository) {
    suspend operator fun invoke(id: String, title: String): PlaylistDetail = repository.renamePlaylist(id, title.trim())
}

class DeletePlaylistUseCase @Inject constructor(private val repository: PlaylistRepository) {
    suspend operator fun invoke(id: String) = repository.deletePlaylist(id)
}

class SetVideoInPlaylistUseCase @Inject constructor(private val repository: PlaylistRepository) {
    suspend operator fun invoke(playlistId: String, videoId: String, inPlaylist: Boolean) =
        repository.setVideoInPlaylist(playlistId, videoId, inPlaylist)
}

class ObservePlaylistChangesUseCase @Inject constructor(private val repository: PlaylistRepository) {
    operator fun invoke(): Flow<Unit> = repository.changes
}
