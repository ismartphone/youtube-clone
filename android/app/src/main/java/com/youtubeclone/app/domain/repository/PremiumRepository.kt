package com.youtubeclone.app.domain.repository

import com.youtubeclone.app.domain.model.PaymentConfig
import com.youtubeclone.app.domain.model.PremiumCheckout
import com.youtubeclone.app.domain.model.PremiumStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * Abonnement Premium de l'utilisateur connecté. Le serveur décide seul de l'activation (ADR-014) :
 * l'application ne fait qu'afficher l'état qu'il renvoie.
 * Les implémentations lèvent une [com.youtubeclone.app.domain.model.DataError] en cas d'échec.
 */
interface PremiumRepository {
    /** Dernier état connu ; null tant qu'il n'a pas été lu. Inactif pour un visiteur. */
    val status: StateFlow<PremiumStatus?>

    /** Relit l'état depuis le serveur (sans session : inactif, sans appel). */
    suspend fun refresh(): PremiumStatus

    /** Mode de paiement actif et, en mode Stripe, la clé publiable. */
    suspend fun getPaymentConfig(): PaymentConfig

    /** Commence un achat : le serveur démarre le paiement, au montant qu'il fixe. */
    suspend fun startCheckout(): PremiumCheckout

    /** Confirme l'achat ; le serveur vérifie le paiement et active l'abonnement s'il l'accepte. */
    suspend fun confirmPurchase(purchaseToken: String): PremiumStatus

    suspend fun cancel(): PremiumStatus
}
