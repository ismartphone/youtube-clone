package com.youtubeclone.app.domain.repository

import com.youtubeclone.app.domain.model.User
import kotlinx.coroutines.flow.Flow

/** Comptes et session. Les implémentations lèvent une [com.youtubeclone.app.domain.model.DataError] en cas d'échec. */
interface AuthRepository {
    /** Vaut true tant qu'une session est ouverte. Passe à false à la déconnexion ou si la session expire. */
    val isLoggedIn: Flow<Boolean>

    /** Identifiant interne (UUID) du compte connecté, ou null hors session. */
    val currentUserId: Flow<String?>

    /** Crée le compte, sans ouvrir de session. */
    suspend fun register(email: String, password: String, displayName: String)

    /** Ouvre une session et la conserve sur l'appareil. */
    suspend fun login(email: String, password: String)

    /** Ferme la session et efface les tokens de l'appareil. */
    suspend fun logout()

    suspend fun getCurrentUser(): User
}
