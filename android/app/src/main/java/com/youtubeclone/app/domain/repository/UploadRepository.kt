package com.youtubeclone.app.domain.repository

import com.youtubeclone.app.domain.model.PickedVideo
import com.youtubeclone.app.domain.model.UploadProgress
import com.youtubeclone.app.domain.model.VideoUpload
import kotlinx.coroutines.flow.Flow

/** Publication depuis l'application (ADR-015). */
interface UploadRepository {
    /** Lit la durée, la taille et l'orientation d'une vidéo choisie ; null si elle est illisible. */
    suspend fun inspect(uri: String): PickedVideo?

    /**
     * Envoie la vidéo et émet son avancement, puis la vidéo publiée. Annuler la collecte annule
     * l'envoi. Lève une [com.youtubeclone.app.domain.model.DataError] en cas d'échec.
     */
    fun upload(upload: VideoUpload): Flow<UploadProgress>
}
