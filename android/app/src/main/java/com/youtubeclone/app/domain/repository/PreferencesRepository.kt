package com.youtubeclone.app.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Préférences propres à cet appareil, sans compte : elles valent aussi pour un visiteur.
 * Rien n'est envoyé au serveur.
 */
interface PreferencesRepository {
    /** Vaut true une fois l'écran d'accueil du premier lancement terminé. */
    val onboardingCompleted: Flow<Boolean>

    /** Identifiants des catégories choisies comme centres d'intérêt, éventuellement vide. */
    val interests: Flow<Set<String>>

    /** « Partager des statistiques d'usage » (ADR-013) : true par défaut ; false coupe tout envoi à PostHog. */
    val shareUsageStats: Flow<Boolean>

    /** « Lecture automatique » : true par défaut ; enchaîne sur la vidéo suggérée à la fin d'une vidéo. */
    val autoplay: Flow<Boolean>

    /** Marque l'écran d'accueil comme vu. */
    suspend fun completeOnboarding()

    suspend fun setInterests(interests: Set<String>)

    suspend fun setShareUsageStats(enabled: Boolean)

    suspend fun setAutoplay(enabled: Boolean)
}
