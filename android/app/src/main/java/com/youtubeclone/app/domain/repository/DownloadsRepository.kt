package com.youtubeclone.app.domain.repository

import com.youtubeclone.app.domain.model.DownloadedVideo
import com.youtubeclone.app.domain.model.Video
import kotlinx.coroutines.flow.Flow

/**
 * Téléchargements hors ligne (ADR-019), ceux du compte actif seulement (ADR-022). Fonctionne sans
 * réseau, sauf pour télécharger et revalider.
 */
interface DownloadsRepository {
    /** Vidéos téléchargées ou en cours, de la plus récente à la plus ancienne, progression comprise. */
    val downloads: Flow<List<DownloadedVideo>>

    /**
     * Demande au serveur l'autorisation, puis lance le téléchargement de [video]. Lève
     * [com.youtubeclone.app.domain.model.DataError.PremiumRequired] sans Premium, et
     * [com.youtubeclone.app.domain.model.DownloadProblem] si la limite ou l'espace manquent.
     */
    suspend fun download(video: Video)

    suspend fun remove(videoId: String)

    /** Supprime les téléchargements de [userId], à sa déconnexion ou à son retrait de l'appareil. */
    suspend fun removeAccount(userId: String)

    /**
     * Téléchargements d'avant l'ADR-022, sans compte : attribués à [userId], le compte actif au
     * moment de la mise à jour, ou supprimés s'il n'y a pas de session (décision 4).
     */
    suspend fun adoptUnowned(userId: String?)

    /**
     * Revalidation par le serveur : les vidéos qui ne sont plus accessibles, ou toutes si le
     * Premium a pris fin, sont supprimées ; les autres repartent pour 30 jours. Sans réseau, rien
     * ne change.
     */
    suspend fun revalidate()

    /** Vidéo à lire hors ligne, ou null si elle n'est pas téléchargée. */
    suspend fun get(videoId: String): DownloadedVideo?
}
