package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.model.MESSAGE_MAX_LENGTH
import com.youtubeclone.app.domain.repository.AuthRepository
import com.youtubeclone.app.domain.repository.MessagingRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Cas d'usage de la messagerie (ADR-025), réunis pour les écrans de conversations. */
class MessagingUseCases @Inject constructor(
    val repository: MessagingRepository,
    private val auth: AuthRepository,
    private val unread: UnreadMessages,
) {
    /** Identifiant du compte actif, pour sa salle personnelle et pour distinguer ses messages. */
    suspend fun currentUserId(): String? = auth.currentUserId.first()

    suspend fun send(conversationId: String, content: String) =
        repository.sendMessage(conversationId, content.trim().take(MESSAGE_MAX_LENGTH))

    /** Marque la conversation comme lue, puis relit la pastille. */
    suspend fun markRead(conversationId: String) {
        repository.markRead(conversationId)
        unread.refresh()
    }
}

/**
 * Nombre de messages non lus, pour la pastille de la barre du haut (ADR-025). Pas de connexion
 * temps réel permanente : il est relu à l'ouverture de l'application, à chaque retour au premier
 * plan, à chaque changement de compte et après la lecture d'une conversation (décision 2).
 */
@Singleton
class UnreadMessages @Inject constructor(
    private val repository: MessagingRepository,
    private val auth: AuthRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    /** Suit le compte actif : un visiteur n'a pas de messages. À appeler une fois, au démarrage. */
    fun start() {
        scope.launch {
            auth.currentUserId.collect { refresh() }
        }
    }

    /** Relit le nombre ; sans réseau, l'ancienne valeur reste affichée. */
    fun refresh() {
        scope.launch {
            if (auth.currentUserId.first() == null) {
                _count.value = 0
                return@launch
            }
            runCatching { repository.getUnreadCount() }.onSuccess { _count.value = it }
        }
    }
}
