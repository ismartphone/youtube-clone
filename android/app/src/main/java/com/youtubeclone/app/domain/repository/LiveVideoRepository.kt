package com.youtubeclone.app.domain.repository

import com.youtubeclone.app.domain.model.LiveVideoEvent
import kotlinx.coroutines.flow.Flow

/** Connexion temps réel à une vidéo (ADR-004). */
interface LiveVideoRepository {
    /**
     * Ouvre la connexion à la collecte et la ferme à l'annulation.
     * En cas de coupure, la connexion est rétablie automatiquement tant que le flux est collecté.
     */
    fun videoEvents(videoId: String): Flow<LiveVideoEvent>
}
