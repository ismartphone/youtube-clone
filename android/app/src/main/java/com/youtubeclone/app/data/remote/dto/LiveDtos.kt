package com.youtubeclone.app.data.remote.dto

import com.youtubeclone.app.data.remote.resolveMediaUrl
import com.youtubeclone.app.domain.model.Channel
import com.youtubeclone.app.domain.model.Live
import com.youtubeclone.app.domain.model.LiveAuthor
import com.youtubeclone.app.domain.model.LiveChatMessage
import com.youtubeclone.app.domain.model.LiveCheckout
import com.youtubeclone.app.domain.model.LiveProduct
import com.youtubeclone.app.domain.model.LiveStatus
import com.youtubeclone.app.domain.model.LiveStream
import java.time.Instant
import kotlinx.serialization.Serializable

// Réponses du module lives du backend (ADR-029).

@Serializable
data class LiveChannelDto(
    val id: String,
    val name: String,
    val handle: String,
    val avatarUrl: String? = null,
)

@Serializable
data class LiveStreamDto(
    val id: String,
    val videoUrl: String,
    val hlsUrl: String? = null,
    val thumbnailUrl: String,
    val durationSeconds: Int,
)

@Serializable
data class LiveProductDto(
    val id: String,
    val name: String,
    val price: Int,
    val currency: String = "eur",
    val imageUrl: String,
    val description: String = "",
)

@Serializable
data class LiveProductListDto(val items: List<LiveProductDto>)

@Serializable
data class PinProductRequestDto(val productId: String)

@Serializable
data class LivePurchaseRequestDto(val purchaseToken: String)

@Serializable
data class LiveCheckoutDto(
    val mode: String,
    val purchaseToken: String,
    val clientSecret: String? = null,
    val publishableKey: String? = null,
)

/** Événement `live:pinned` : [product] est null quand le produit est retiré. */
@Serializable
data class LivePinnedDto(val liveId: String, val product: LiveProductDto? = null)

@Serializable
data class LiveDto(
    val id: String,
    val title: String,
    val status: String,
    val startedAt: String,
    val viewerCount: Int = 0,
    val channel: LiveChannelDto,
    val video: LiveStreamDto? = null,
    val pinnedProduct: LiveProductDto? = null,
)

@Serializable
data class LiveListDto(val items: List<LiveDto>)

@Serializable
data class LiveAuthorDto(
    val id: String,
    val displayName: String,
    val avatarUrl: String? = null,
)

@Serializable
data class LiveMessageDto(
    val id: String,
    val liveId: String,
    // CHAT, ou PURCHASE pour l'annonce d'un achat écrite par le serveur.
    val kind: String = "CHAT",
    val content: String,
    val createdAt: String,
    val author: LiveAuthorDto,
)

@Serializable
data class LiveMessageListDto(val items: List<LiveMessageDto>)

@Serializable
data class StartLiveRequestDto(val title: String, val videoId: String)

@Serializable
data class LiveMessageRequestDto(val content: String)

@Serializable
data class LiveReactionRequestDto(val emoji: String)

/** Événement `live:reaction`. */
@Serializable
data class LiveReactionDto(val liveId: String, val emoji: String)

/** Événement `live:viewers`. */
@Serializable
data class LiveViewersDto(val liveId: String, val viewerCount: Int)

fun LiveDto.toDomain() = Live(
    id = id,
    title = title,
    status = if (status == "ENDED") LiveStatus.Ended else LiveStatus.Live,
    startedAt = Instant.parse(startedAt),
    viewerCount = viewerCount,
    channel = Channel(
        id = channel.id,
        name = channel.name,
        handle = channel.handle,
        avatarUrl = channel.avatarUrl?.let(::resolveMediaUrl),
    ),
    stream = video?.let {
        LiveStream(
            videoId = it.id,
            videoUrl = resolveMediaUrl(it.videoUrl),
            hlsUrl = it.hlsUrl?.let(::resolveMediaUrl),
            thumbnailUrl = resolveMediaUrl(it.thumbnailUrl),
            durationSeconds = it.durationSeconds,
        )
    },
    pinnedProduct = pinnedProduct?.toDomain(),
)

fun LiveProductDto.toDomain() = LiveProduct(
    id = id,
    name = name,
    priceCents = price,
    currency = currency,
    imageUrl = resolveMediaUrl(imageUrl),
    description = description,
)

fun LiveCheckoutDto.toDomain() = LiveCheckout(
    mode = paymentMode(mode),
    purchaseToken = purchaseToken,
    clientSecret = clientSecret,
    publishableKey = publishableKey,
)

fun LiveMessageDto.toDomain() = LiveChatMessage(
    id = id,
    liveId = liveId,
    content = content,
    createdAt = Instant.parse(createdAt),
    author = LiveAuthor(author.id, author.displayName, author.avatarUrl?.let(::resolveMediaUrl)),
    isPurchase = kind == "PURCHASE",
)
