package com.youtubeclone.app.presentation.messaging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeclone.app.domain.model.Conversation
import com.youtubeclone.app.domain.model.IncomingMessage
import com.youtubeclone.app.domain.model.LiveMessagingEvent
import com.youtubeclone.app.domain.usecase.MessagingUseCases
import com.youtubeclone.app.presentation.common.UiError
import com.youtubeclone.app.presentation.common.toUiError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ConversationsUiState {
    data object Loading : ConversationsUiState
    data class Error(val error: UiError) : ConversationsUiState
    data class Ready(val conversations: List<Conversation>, val myId: String) : ConversationsUiState
}

/**
 * Liste des conversations (ADR-025) : avatar, nom, dernier message, date, non-lus. Écran ouvert,
 * la salle personnelle de l'utilisateur apporte les nouveaux messages en direct, y compris ceux
 * d'une conversation que l'autre vient de démarrer.
 */
@HiltViewModel
class ConversationsViewModel @Inject constructor(
    private val messaging: MessagingUseCases,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ConversationsUiState>(ConversationsUiState.Loading)
    val uiState: StateFlow<ConversationsUiState> = _uiState.asStateFlow()

    private var hasConnectedOnce = false

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = try {
                val myId = messaging.currentUserId() ?: return@launch
                ConversationsUiState.Ready(messaging.repository.getConversations(), myId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Liste déjà affichée : on la garde plutôt que de la remplacer par une erreur.
                _uiState.value as? ConversationsUiState.Ready ?: ConversationsUiState.Error(e.toUiError())
            }
        }
    }

    /** Appelée par l'écran tant qu'il est visible : la connexion se ferme quand on le quitte. */
    suspend fun collectLiveUpdates() {
        val myId = messaging.currentUserId() ?: return
        messaging.repository.inboxEvents(myId).collect { event ->
            when (event) {
                LiveMessagingEvent.Connected -> {
                    // Retour sur l'écran ou reconnexion : ce qui a été manqué est relu.
                    if (hasConnectedOnce) load()
                    hasConnectedOnce = true
                }
                is LiveMessagingEvent.MessageReceived -> onMessage(event.incoming)
            }
        }
    }

    // Le message remonte sa conversation en tête, avec un non-lu de plus ; une conversation
    // inconnue (démarrée par l'autre) apparaît, avec l'expéditeur comme interlocuteur.
    private fun onMessage(incoming: IncomingMessage) {
        _uiState.update { state ->
            if (state !is ConversationsUiState.Ready) return@update state
            val message = incoming.message
            val existing = state.conversations.firstOrNull { it.id == message.conversationId }
            val updated = (existing ?: Conversation(message.conversationId, incoming.sender, null, 0)).let {
                it.copy(lastMessage = message, unreadCount = it.unreadCount + 1)
            }
            state.copy(conversations = listOf(updated) + state.conversations.filterNot { it.id == updated.id })
        }
    }
}
