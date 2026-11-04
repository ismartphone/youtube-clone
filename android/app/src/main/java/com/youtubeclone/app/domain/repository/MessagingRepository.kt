package com.youtubeclone.app.domain.repository

import com.youtubeclone.app.domain.model.Conversation
import com.youtubeclone.app.domain.model.LiveMessagingEvent
import com.youtubeclone.app.domain.model.Message
import com.youtubeclone.app.domain.model.MessagePage
import com.youtubeclone.app.domain.model.Person
import kotlinx.coroutines.flow.Flow

/**
 * Messagerie entre utilisateurs (ADR-025), réservée aux comptes connectés. Les implémentations
 * lèvent une [com.youtubeclone.app.domain.model.DataError] en cas d'échec.
 */
interface MessagingRepository {
    /** Conversations, triées par dernier message. */
    suspend fun getConversations(page: Int = 1): List<Conversation>

    suspend fun getConversation(conversationId: String): Conversation

    /** Conversation existante avec [userId], ou nouvelle. */
    suspend fun startConversation(userId: String): Conversation

    /** Du plus récent au plus ancien. */
    suspend fun getMessages(conversationId: String, page: Int): MessagePage

    suspend fun sendMessage(conversationId: String, content: String): Message

    suspend fun markRead(conversationId: String)

    suspend fun getUnreadCount(): Int

    /** Recherche d'un destinataire sur son nom affiché, soi-même exclu. */
    suspend fun searchPeople(query: String): List<Person>

    /** Messages de la conversation, en direct, tant que le flux est collecté. */
    fun conversationEvents(conversationId: String): Flow<LiveMessagingEvent>

    /** Messages reçus par [userId] dans toutes ses conversations, en direct (liste des conversations). */
    fun inboxEvents(userId: String): Flow<LiveMessagingEvent>
}
