package com.youtubeclone.app.domain.model

import java.time.Instant

// Live shopping (ADR-029). Le direct est simulé : son flux est une vidéo du catalogue lue en
// boucle. Le chat, le compteur de spectateurs et la fin du live, eux, sont réels.

/** Abonnés nécessaires pour passer en direct (ADR-015) ; le serveur le revérifie. */
const val LIVE_MIN_FOLLOWERS = 50

/** Mêmes limites que l'API : titre du live et message du chat (ADR-029, D5). */
const val LIVE_TITLE_MAX_LENGTH = 100
const val LIVE_CHAT_MAX_LENGTH = 300

enum class LiveStatus { Live, Ended }

/** Produit épinglé par le diffuseur (ADR-029) : carte affichée en direct chez tous les spectateurs. */
data class LiveProduct(
    val id: String,
    val name: String,
    /** En centimes. */
    val priceCents: Int,
    /** Devise ISO en minuscules (« eur »). */
    val currency: String,
    val imageUrl: String,
    val description: String,
)

/** Mode de paiement d'un achat de live : Stripe en mode test, ou le repli simulé (ADR-014). */
data class LiveCheckout(
    val mode: PaymentMode,
    val purchaseToken: String,
    val clientSecret: String?,
    val publishableKey: String?,
)

/** Vidéo du catalogue lue en boucle comme flux du live. */
data class LiveStream(
    val videoId: String,
    val videoUrl: String,
    val hlsUrl: String?,
    val thumbnailUrl: String,
    val durationSeconds: Int,
)

/**
 * Un live tel que le serveur le décrit. [stream] est null si la vidéo a été supprimée, ce qui
 * termine aussi le live. [viewerCount] est celui de la salle au moment de la lecture.
 */
data class Live(
    val id: String,
    val title: String,
    val status: LiveStatus,
    val startedAt: Instant,
    val viewerCount: Int,
    val channel: Channel,
    val stream: LiveStream?,
    val pinnedProduct: LiveProduct? = null,
) {
    val isLive: Boolean get() = status == LiveStatus.Live

    /**
     * Position de lecture commune à tous les spectateurs (ADR-029, D3) : le temps écoulé depuis le
     * début du live, modulo la durée du fichier. 0 si la durée est inconnue.
     */
    fun playbackPositionMs(now: Instant): Long {
        val durationMs = (stream?.durationSeconds ?: 0) * 1000L
        if (durationMs <= 0) return 0
        val elapsed = (now.toEpochMilli() - startedAt.toEpochMilli()).coerceAtLeast(0)
        return elapsed % durationMs
    }
}

/** Auteur d'un message du chat : son nom et son avatar, jamais son email. */
data class LiveAuthor(
    val id: String,
    val displayName: String,
    val avatarUrl: String?,
)

data class LiveChatMessage(
    val id: String,
    val liveId: String,
    val content: String,
    val createdAt: Instant,
    val author: LiveAuthor,
    /** Annonce d'un achat écrite par le serveur, à distinguer d'un message du chat (ADR-029). */
    val isPurchase: Boolean = false,
)

/** Événements reçus en direct dans la salle du live. */
sealed interface LiveRoomEvent {
    /**
     * La connexion est (r)établie. Ce qui a été diffusé pendant une coupure est perdu : après une
     * reconnexion, l'écran relit l'état du live et les derniers messages.
     */
    data object Connected : LiveRoomEvent

    data class MessageReceived(val message: LiveChatMessage) : LiveRoomEvent
    data class ViewersChanged(val viewerCount: Int) : LiveRoomEvent

    /** Un spectateur a réagi : [emoji] remonte à l'écran. Jamais stockée, donc jamais rejouée. */
    data class ReactionReceived(val emoji: String) : LiveRoomEvent

    /** Le diffuseur a épinglé [product], ou l'a retiré (null) : la carte apparaît ou disparaît. */
    data class ProductPinned(val product: LiveProduct?) : LiveRoomEvent

    /** Le diffuseur a terminé le live. */
    data object Ended : LiveRoomEvent
}

/** Réactions proposées (ADR-029, D6) : la même liste fixe que celle du serveur, qui refuse tout autre émoji. */
val LIVE_REACTIONS = listOf("❤️", "👏", "😂", "😮", "🔥", "👍")

/** Plafond d'émojis affichés en même temps : au-delà, les plus anciens disparaissent. */
const val LIVE_REACTIONS_MAX_ON_SCREEN = 12
