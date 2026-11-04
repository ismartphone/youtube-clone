package com.youtubeclone.app.data.remote

import com.youtubeclone.app.data.remote.dto.AddVideoRequestDto
import com.youtubeclone.app.data.remote.dto.PlaylistDetailDto
import com.youtubeclone.app.data.remote.dto.PlaylistSummaryDto
import com.youtubeclone.app.data.remote.dto.PlaylistTitleRequestDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Playlists de l'utilisateur connecté (ADR-019) : routes protégées, privées. */
interface PlaylistApi {
    // Avec [videoId], chaque playlist indique si elle contient déjà cette vidéo.
    @GET("me/playlists")
    suspend fun getPlaylists(@Query("videoId") videoId: String? = null): List<PlaylistSummaryDto>

    @POST("me/playlists")
    suspend fun createPlaylist(@Body body: PlaylistTitleRequestDto): PlaylistSummaryDto

    @GET("me/playlists/{id}")
    suspend fun getPlaylist(@Path("id") id: String): PlaylistDetailDto

    @PATCH("me/playlists/{id}")
    suspend fun renamePlaylist(@Path("id") id: String, @Body body: PlaylistTitleRequestDto): PlaylistDetailDto

    @DELETE("me/playlists/{id}")
    suspend fun deletePlaylist(@Path("id") id: String)

    @POST("me/playlists/{id}/videos")
    suspend fun addVideo(@Path("id") id: String, @Body body: AddVideoRequestDto)

    @DELETE("me/playlists/{id}/videos/{videoId}")
    suspend fun removeVideo(@Path("id") id: String, @Path("videoId") videoId: String)
}
