package com.youtubeclone.app.data.remote

import com.youtubeclone.app.data.remote.dto.CheckDownloadsRequestDto
import com.youtubeclone.app.data.remote.dto.DownloadCheckDto
import com.youtubeclone.app.data.remote.dto.DownloadGrantDto
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/** Téléchargements hors ligne (ADR-019) : routes protégées, réservées au Premium. */
interface DownloadApi {
    // Adresse du fichier à télécharger, après vérification du Premium et de l'accès.
    @POST("videos/{id}/download")
    suspend fun authorize(@Path("id") id: String): DownloadGrantDto

    // Vidéos téléchargées qui restent lisibles, et fin de l'abonnement.
    @POST("me/downloads/check")
    suspend fun check(@Body body: CheckDownloadsRequestDto): DownloadCheckDto
}
