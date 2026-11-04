package com.youtubeclone.app.domain.repository

import com.youtubeclone.app.domain.model.Account
import com.youtubeclone.app.domain.model.User
import kotlinx.coroutines.flow.Flow

/** Comptes et session. Les implémentations lèvent une [com.youtubeclone.app.domain.model.DataError] en cas d'échec. */
interface AuthRepository {
    /** Vaut true tant qu'une session est ouverte. Passe à false à la déconnexion ou si la session expire. */
    val isLoggedIn: Flow<Boolean>

    /** Identifiant interne (UUID) du compte connecté, ou null hors session. */
    val currentUserId: Flow<String?>

    /** Comptes enregistrés sur l'appareil (ADR-022), compte actif compris. */
    val accounts: Flow<List<Account>>

    /** Crée le compte, sans ouvrir de session. */
    suspend fun register(email: String, password: String, displayName: String)

    /** Ouvre une session et la conserve sur l'appareil. */
    suspend fun login(email: String, password: String)

    /** Retire le compte actif de l'appareil (tokens et clé) ; on devient visiteur. */
    suspend fun logout()

    /** Fait de [userId] le compte actif sans mot de passe ; false si sa session a expiré. */
    suspend fun switchTo(userId: String): Boolean

    /** Retire un compte inactif de l'appareil, sans appel au serveur (ADR-022, décision 10). */
    suspend fun removeAccount(userId: String)

    /** Relit le profil du compte actif (nom, email, avatar, @handle de sa chaîne) pour la feuille des comptes. */
    suspend fun refreshActiveProfile()

    suspend fun getCurrentUser(): User
}
