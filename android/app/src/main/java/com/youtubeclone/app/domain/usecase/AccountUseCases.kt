package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.model.Account
import com.youtubeclone.app.domain.repository.AuthRepository
import com.youtubeclone.app.domain.repository.DownloadsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Comptes enregistrés sur l'appareil, pour la feuille des comptes (ADR-022). */
class ObserveAccountsUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    operator fun invoke(): Flow<List<Account>> = repository.accounts
}

/**
 * Bascule vers un autre compte enregistré, sans mot de passe (ADR-022). Renvoie false si sa
 * session a expiré : il faut alors passer par la connexion, email prérempli (décision 2).
 *
 * Le reste suit le compte actif de lui-même : jeton de notification (PushTokenSync), mesures
 * (reset puis identify), revalidation des téléchargements (DownloadsSync), profil de la feuille
 * (AccountsViewModel). L'écran, lui, arrête
 * la lecture et revient au fil, pile de navigation vidée.
 */
class SwitchAccountUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(userId: String): Boolean = repository.switchTo(userId)
}

/**
 * Retire un compte depuis « Gérer les comptes ». Compte actif : comme une déconnexion. Compte
 * inactif : ses tokens, sa clé et ses téléchargements sont supprimés, sans appel au serveur, le
 * jeton de notification appartenant au compte actif (décision 10).
 */
class RemoveAccountUseCase @Inject constructor(
    private val repository: AuthRepository,
    private val downloads: DownloadsRepository,
    private val logout: LogoutUseCase,
) {
    suspend operator fun invoke(account: Account) {
        if (account.isActive) {
            logout()
            return
        }
        downloads.removeAccount(account.userId)
        repository.removeAccount(account.userId)
    }
}

/**
 * Relit le profil du compte actif (nom, email, avatar, @handle de sa chaîne) pour la feuille des
 * comptes : au démarrage, la session reprise d'avant l'ADR-022 n'a encore ni nom ni email.
 * Sans réseau, le profil déjà enregistré reste affiché.
 */
class RefreshAccountProfileUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke() {
        runCatching { repository.refreshActiveProfile() }
    }
}
