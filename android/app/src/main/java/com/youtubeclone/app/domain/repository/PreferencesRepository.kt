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

    /** Marque l'écran d'accueil comme vu. */
    suspend fun completeOnboarding()

    suspend fun setInterests(interests: Set<String>)
}
