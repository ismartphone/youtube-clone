package com.youtubeclone.app.data.remote.dto

import com.youtubeclone.app.domain.model.PaymentConfig
import com.youtubeclone.app.domain.model.PaymentMode
import com.youtubeclone.app.domain.model.PremiumCheckout
import com.youtubeclone.app.domain.model.PremiumStatus
import java.time.Instant
import kotlinx.serialization.Serializable

// Réponses du module premium du backend (premium.controller.ts).

@Serializable
data class PremiumStatusDto(
    val isPremium: Boolean,
    val expiresAt: String? = null,
)

@Serializable
data class PaymentConfigDto(
    val mode: String,
    val publishableKey: String? = null,
)

@Serializable
data class CheckoutDto(
    val mode: String,
    val purchaseToken: String,
    val clientSecret: String? = null,
)

@Serializable
data class SubscribeRequest(
    val purchaseToken: String,
)

// Mode inconnu (version plus récente du serveur) : repli sur l'achat simulé.
internal fun paymentMode(mode: String) = if (mode == "stripe") PaymentMode.STRIPE else PaymentMode.SIMULATED

fun PaymentConfigDto.toDomain() = PaymentConfig(mode = paymentMode(mode), publishableKey = publishableKey)

fun CheckoutDto.toDomain() = PremiumCheckout(
    mode = paymentMode(mode),
    purchaseToken = purchaseToken,
    clientSecret = clientSecret,
)

fun PremiumStatusDto.toDomain() = PremiumStatus(
    isPremium = isPremium,
    expiresAt = expiresAt?.let(Instant::parse),
)
