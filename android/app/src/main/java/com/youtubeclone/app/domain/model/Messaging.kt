package com.youtubeclone.app.domain.model

import java.time.Instant

/** Message : 2000 caractères au plus, comme l'API (ADR-025). */
const val MESSAGE_MAX_LENGTH = 2000

/**
 * Personne dans la messagerie (ADR-025). [handle] : @handle de sa chaîne, pour départager les
 * homonymes ; null sans chaîne.
 */
data class Person(
    val id: String,
    val displayName: String,
    val avatarUrl: String?,
    val handle: String?,
)

/**
 * Vidéo partagée dans un message (ADR-025, étape 2), telle que le serveur la montre à l'utilisateur
 * au moment de la lecture : supprimée depuis, ou privée pour lui, elle est indisponible.
 */
sealed interface SharedVideo {
    data object Unavailable : SharedVideo

    data class Available(
        val id: String,
        val title: String,
        val thumbnailUrl: String?,
        val channelName: String,
        val isShort: Boolean,
        val isPremium: Boolean,
    ) : SharedVideo
}

data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    /** Vide quand une vidéo est partagée sans texte. */
    val content: String,
    val createdAt: Instant,
    /** null pour un message sans vidéo. */
    val video: SharedVideo? = null,
)

/** Conversation vue par l'utilisateur : [other] est l'autre participant. */
data class Conversation(
    val id: String,
    val other: Person,
    val lastMessage: Message?,
    /** Messages de l'autre arrivés après la dernière lecture. */
    val unreadCount: Int,
)

data class MessagePage(val items: List<Message>, val page: Int, val total: Int)

/** Message reçu en direct (message:new), avec son expéditeur. */
data class IncomingMessage(val message: Message, val sender: Person)

/** Événements de la messagerie reçus en direct, écran de messagerie ouvert (ADR-025). */
sealed interface LiveMessagingEvent {
    /** La connexion est (r)établie : ce qui a été diffusé pendant une coupure est perdu. */
    data object Connected : LiveMessagingEvent

    data class MessageReceived(val incoming: IncomingMessage) : LiveMessagingEvent
}
