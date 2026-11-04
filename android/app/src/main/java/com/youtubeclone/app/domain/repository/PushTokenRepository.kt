package com.youtubeclone.app.domain.repository

/**
 * Jeton de notification de l'appareil (ADR-020), connu du serveur tant qu'une session est ouverte.
 */
interface PushTokenRepository {
    /** Enregistre le jeton auprès du serveur : [token] s'il vient d'être renouvelé, sinon le jeton courant. */
    suspend fun register(token: String? = null)

    /**
     * À la déconnexion, avant la fermeture de la session : retire le jeton du serveur si le réseau
     * le permet, puis le supprime de l'appareil, ce qui coupe les notifications même hors ligne.
     */
    suspend fun unregister()
}
