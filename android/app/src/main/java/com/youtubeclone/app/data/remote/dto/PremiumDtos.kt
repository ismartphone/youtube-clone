package com.youtubeclone.app.data.remote.dto

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
data class CheckoutDto(
    val purchaseToken: String,
)

@Serializable
data class SubscribeRequest(
    val purchaseToken: String,
)

fun PremiumStatusDto.toDomain() = PremiumStatus(
    isPremium = isPremium,
    expiresAt = expiresAt?.let(Instant::parse),
)
