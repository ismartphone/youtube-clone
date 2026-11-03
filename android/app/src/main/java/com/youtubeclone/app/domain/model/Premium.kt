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
