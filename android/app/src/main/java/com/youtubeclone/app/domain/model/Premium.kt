package com.youtubeclone.app.domain.model

import java.time.Instant

/**
 * Abonnement Premium de l'utilisateur (PREMIUM_SUBSCRIPTION), à ne pas confondre avec
 * l'abonnement gratuit à une chaîne. [expiresAt] : date de fin, null hors abonnement.
 */
data class PremiumStatus(
    val isPremium: Boolean,
    val expiresAt: Instant?,
) {
    companion object {
        val Inactive = PremiumStatus(isPremium = false, expiresAt = null)
    }
}

/** Mode de paiement choisi par le serveur (ADR-014) : Stripe en mode test, ou achat simulé de repli. */
enum class PaymentMode { STRIPE, SIMULATED }

/** Configuration du paiement : [publishableKey] (clé publiable Stripe) n'existe qu'en mode Stripe. */
data class PaymentConfig(
    val mode: PaymentMode,
    val publishableKey: String?,
)

/**
 * Paiement démarré par le serveur. [purchaseToken] est renvoyé pour confirmer : identifiant du
 * PaymentIntent en mode Stripe, jeton simulé sinon. [clientSecret] sert à la PaymentSheet Stripe.
 */
data class PremiumCheckout(
    val mode: PaymentMode,
    val purchaseToken: String,
    val clientSecret: String?,
)
