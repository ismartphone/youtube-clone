package com.youtubeclone.app.data.remote.dto

import com.youtubeclone.app.data.remote.resolveMediaUrl
import com.youtubeclone.app.domain.model.Conversation
import com.youtubeclone.app.domain.model.IncomingMessage
import com.youtubeclone.app.domain.model.Message
import com.youtubeclone.app.domain.model.MessagePage
import com.youtubeclone.app.domain.model.Person
import java.time.Instant
import kotlinx.serialization.Serializable

// Réponses du module messaging du backend (ADR-025). Même forme d'un message en REST et dans
// l'événement message:new, qui y ajoute son expéditeur (sender).

@Serializable
data class PersonDto(
    val id: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val handle: String? = null,
)

@Serializable
data class MessageDto(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val content: String,
    val createdAt: String,
)

@Serializable
data class IncomingMessageDto(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val content: String,
    val createdAt: String,
    val sender: PersonDto,
)

@Serializable
data class ConversationDto(
    val id: String,
    val other: PersonDto,
    val lastMessage: MessageDto? = null,
    val unreadCount: Int = 0,
)

@Serializable
data class ConversationPageDto(val items: List<ConversationDto>, val page: Int, val total: Int)

@Serializable
data class MessagePageDto(val items: List<MessageDto>, val page: Int, val total: Int)

@Serializable
data class PeopleDto(val items: List<PersonDto>)

@Serializable
data class UnreadCountDto(val unreadCount: Int)

@Serializable
data class StartConversationRequestDto(val userId: String)

@Serializable
data class SendMessageRequestDto(val content: String)

fun PersonDto.toDomain() = Person(id, displayName, avatarUrl?.let(::resolveMediaUrl), handle)

fun MessageDto.toDomain() = Message(id, conversationId, senderId, content, Instant.parse(createdAt))

fun IncomingMessageDto.toDomain() = IncomingMessage(
    message = Message(id, conversationId, senderId, content, Instant.parse(createdAt)),
    sender = sender.toDomain(),
)

fun ConversationDto.toDomain() = Conversation(id, other.toDomain(), lastMessage?.toDomain(), unreadCount)

fun MessagePageDto.toDomain() = MessagePage(items.map { it.toDomain() }, page, total)
