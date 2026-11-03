package com.youtubeclone.app.domain.repository

import com.youtubeclone.app.domain.model.PremiumStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * Abonnement Premium de l'utilisateur connecté. Le serveur décide seul de l'activation (ADR-012) :
 * l'application ne fait qu'afficher l'état qu'il renvoie.
 * Les implémentations lèvent une [com.youtubeclone.app.domain.model.DataError] en cas d'échec.
 */
interface PremiumRepository {
    /** Dernier état connu ; null tant qu'il n'a pas été lu. Inactif pour un visiteur. */
    val status: StateFlow<PremiumStatus?>

    /** Relit l'état depuis le serveur (sans session : inactif, sans appel). */
    suspend fun refresh(): PremiumStatus

    /** Commence un achat : renvoie le jeton émis par le serveur. */
    suspend fun startCheckout(): String

    /** Confirme l'achat avec le jeton ; le serveur active l'abonnement s'il l'accepte. */
    suspend fun confirmPurchase(purchaseToken: String): PremiumStatus

    suspend fun cancel(): PremiumStatus
}
