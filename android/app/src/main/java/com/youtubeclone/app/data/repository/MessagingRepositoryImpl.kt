package com.youtubeclone.app.data.repository

import com.youtubeclone.app.data.realtime.RealtimeSocket
import com.youtubeclone.app.data.remote.MessagingApi
import com.youtubeclone.app.data.remote.apiCall
import com.youtubeclone.app.data.remote.dto.IncomingMessageDto
import com.youtubeclone.app.data.remote.dto.SendMessageRequestDto
import com.youtubeclone.app.data.remote.dto.StartConversationRequestDto
import com.youtubeclone.app.data.remote.dto.toDomain
import com.youtubeclone.app.domain.model.Conversation
import com.youtubeclone.app.domain.model.LiveMessagingEvent
import com.youtubeclone.app.domain.model.Message
import com.youtubeclone.app.domain.model.MessagePage
import com.youtubeclone.app.domain.model.Person
import com.youtubeclone.app.domain.repository.MessagingRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class MessagingRepositoryImpl @Inject constructor(
    private val api: MessagingApi,
    private val socket: RealtimeSocket,
    private val json: Json,
) : MessagingRepository {

    override suspend fun getConversations(page: Int): List<Conversation> =
        apiCall(json) { api.getConversations(page) }.items.map { it.toDomain() }

    override suspend fun getConversation(conversationId: String): Conversation =
        apiCall(json) { api.getConversation(conversationId) }.toDomain()

    override suspend fun startConversation(userId: String): Conversation =
        apiCall(json) { api.startConversation(StartConversationRequestDto(userId)) }.toDomain()

    override suspend fun getMessages(conversationId: String, page: Int): MessagePage =
        apiCall(json) { api.getMessages(conversationId, page) }.toDomain()

    // apiCall garde le message du serveur (par exemple un refus de validation) pour l'afficher.
    override suspend fun sendMessage(conversationId: String, content: String): Message =
        apiCall(json) { api.sendMessage(conversationId, SendMessageRequestDto(content)) }.toDomain()

    override suspend fun markRead(conversationId: String) = apiCall(json) { api.markRead(conversationId) }

    override suspend fun getUnreadCount(): Int = apiCall(json) { api.getUnreadCount() }.unreadCount

    override suspend fun searchPeople(query: String): List<Person> =
        apiCall(json) { api.searchPeople(query) }.items.map { it.toDomain() }

    override fun conversationEvents(conversationId: String): Flow<LiveMessagingEvent> =
        socket.events("conversationId" to conversationId, LiveMessagingEvent.Connected, ::parse)

    override fun inboxEvents(userId: String): Flow<LiveMessagingEvent> =
        socket.events("userId" to userId, LiveMessagingEvent.Connected, ::parse)

    private fun parse(event: String, data: JsonObject): LiveMessagingEvent? = when (event) {
        "message:new" -> LiveMessagingEvent.MessageReceived(
            json.decodeFromJsonElement(IncomingMessageDto.serializer(), data).toDomain(),
        )
        else -> null
    }
}
