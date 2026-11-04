package com.youtubeclone.app.data.repository

import com.youtubeclone.app.data.remote.PlaylistApi
import com.youtubeclone.app.data.remote.apiCall
import com.youtubeclone.app.data.remote.dto.AddVideoRequestDto
import com.youtubeclone.app.data.remote.dto.PlaylistTitleRequestDto
import com.youtubeclone.app.data.remote.dto.toDomain
import com.youtubeclone.app.domain.model.PlaylistDetail
import com.youtubeclone.app.domain.model.PlaylistSummary
import com.youtubeclone.app.domain.repository.PlaylistRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json

class PlaylistRepositoryImpl @Inject constructor(
    private val api: PlaylistApi,
    private val json: Json,
) : PlaylistRepository {

    // Instance unique (voir RepositoryModule) : Vous et l'écran d'une playlist suivent les mêmes changements.
    private val changed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val changes: Flow<Unit> = changed.asSharedFlow()

    override suspend fun getPlaylists(videoId: String?): List<PlaylistSummary> =
        apiCall(json) { api.getPlaylists(videoId) }.map { it.toDomain() }

    override suspend fun createPlaylist(title: String): PlaylistSummary =
        apiCall(json) { api.createPlaylist(PlaylistTitleRequestDto(title)) }.toDomain().also { changed.tryEmit(Unit) }

    override suspend fun getPlaylist(id: String): PlaylistDetail = apiCall(json) { api.getPlaylist(id) }.toDomain()

    override suspend fun renamePlaylist(id: String, title: String): PlaylistDetail =
        apiCall(json) { api.renamePlaylist(id, PlaylistTitleRequestDto(title)) }.toDomain().also { changed.tryEmit(Unit) }

    override suspend fun deletePlaylist(id: String) {
        apiCall(json) { api.deletePlaylist(id) }
        changed.tryEmit(Unit)
    }

    override suspend fun setVideoInPlaylist(playlistId: String, videoId: String, inPlaylist: Boolean) {
        apiCall(json) {
            if (inPlaylist) api.addVideo(playlistId, AddVideoRequestDto(videoId)) else api.removeVideo(playlistId, videoId)
        }
        changed.tryEmit(Unit)
    }
}
