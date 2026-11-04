package com.youtubeclone.app.presentation.messaging

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.youtubeclone.app.domain.model.LiveMessagingEvent
import com.youtubeclone.app.domain.model.MESSAGE_MAX_LENGTH
import com.youtubeclone.app.domain.model.Message
import com.youtubeclone.app.domain.model.Person
import com.youtubeclone.app.domain.usecase.MessagingUseCases
import com.youtubeclone.app.presentation.common.UiError
import com.youtubeclone.app.presentation.common.toUiError
import com.youtubeclone.app.presentation.navigation.ConversationRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ConversationUiState {
    data object Loading : ConversationUiState
    data class Error(val error: UiError) : ConversationUiState
    data class Ready(
        val other: Person,
        val myId: String,
        /** Du plus récent au plus ancien : la liste s'affiche à l'envers, le plus récent en bas. */
        val messages: List<Message>,
        val total: Int,
        val isLoadingMore: Boolean = false,
    ) : ConversationUiState {
        val hasMore: Boolean get() = messages.size < total
    }
}

/**
 * Une conversation (ADR-025) : messages en bulles, envoi, réception en direct tant que l'écran est
 * ouvert, et marquage comme lu à l'ouverture et à chaque message reçu.
 */
@HiltViewModel
class ConversationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messaging: MessagingUseCases,
) : ViewModel() {

    val conversationId = savedStateHandle.toRoute<ConversationRoute>().conversationId

    private val _uiState = MutableStateFlow<ConversationUiState>(ConversationUiState.Loading)
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _errors = MutableSharedFlow<UiError>(extraBufferCapacity = 1)
    val errors: SharedFlow<UiError> = _errors.asSharedFlow()

    private var loadedPages = 0
    private var hasConnectedOnce = false

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                val myId = messaging.currentUserId() ?: return@launch
                val conversation = messaging.repository.getConversation(conversationId)
                val page = messaging.repository.getMessages(conversationId, page = 1)
                loadedPages = 1
                _uiState.value = ConversationUiState.Ready(conversation.other, myId, page.items, page.total)
                markRead()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_uiState.value !is ConversationUiState.Ready) _uiState.value = ConversationUiState.Error(e.toUiError())
            }
        }
    }

    /** Messages plus anciens, quand on remonte en haut de la conversation. */
    fun loadMore() {
        val state = _uiState.value as? ConversationUiState.Ready ?: return
        if (state.isLoadingMore || !state.hasMore) return
        _uiState.value = state.copy(isLoadingMore = true)
        viewModelScope.launch {
            try {
                val page = messaging.repository.getMessages(conversationId, page = loadedPages + 1)
                loadedPages += 1
                updateReady { ready ->
                    // Des messages arrivés entre-temps décalent les pages : les doublons sont ignorés.
                    val known = ready.messages.mapTo(HashSet()) { it.id }
                    ready.copy(messages = ready.messages + page.items.filterNot { it.id in known }, total = page.total, isLoadingMore = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateReady { it.copy(isLoadingMore = false) }
                _errors.tryEmit(e.toUiError())
            }
        }
    }

    fun onDraftChange(text: String) {
        _draft.value = text.take(MESSAGE_MAX_LENGTH)
    }

    fun send() {
        val text = _draft.value
        if (text.isBlank() || _isSending.value) return
        _isSending.value = true
        viewModelScope.launch {
            try {
                add(messaging.send(conversationId, text))
                _draft.value = ""
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Le brouillon est gardé, pour pouvoir réessayer.
                _errors.tryEmit(e.toUiError())
            } finally {
                _isSending.value = false
            }
        }
    }

    /** Appelée par l'écran tant qu'il est visible : la connexion se ferme quand on le quitte. */
    suspend fun collectLiveUpdates() {
        messaging.repository.conversationEvents(conversationId).collect { event ->
            when (event) {
                LiveMessagingEvent.Connected -> {
                    // Retour sur l'écran ou reconnexion : ce qui a été manqué est relu.
                    if (hasConnectedOnce) load()
                    hasConnectedOnce = true
                }
                is LiveMessagingEvent.MessageReceived -> {
                    add(event.incoming.message)
                    // Lu aussitôt : la conversation est à l'écran.
                    if (event.incoming.message.senderId != (_uiState.value as? ConversationUiState.Ready)?.myId) markRead()
                }
            }
        }
    }

    // Notre propre message arrive deux fois (réponse du POST et diffusion) : une seule bulle.
    private fun add(message: Message) {
        updateReady { ready ->
            if (ready.messages.any { it.id == message.id }) {
                ready
            } else {
                ready.copy(messages = listOf(message) + ready.messages, total = ready.total + 1)
            }
        }
    }

    private fun markRead() {
        viewModelScope.launch { runCatching { messaging.markRead(conversationId) } }
    }

    private fun updateReady(change: (ConversationUiState.Ready) -> ConversationUiState.Ready) {
        _uiState.update { if (it is ConversationUiState.Ready) change(it) else it }
    }
}
