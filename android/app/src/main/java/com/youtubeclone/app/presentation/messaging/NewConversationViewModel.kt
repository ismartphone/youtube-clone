package com.youtubeclone.app.presentation.messaging

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.youtubeclone.app.domain.model.Person
import com.youtubeclone.app.domain.usecase.MessagingUseCases
import com.youtubeclone.app.presentation.common.UiError
import com.youtubeclone.app.presentation.common.toUiError
import com.youtubeclone.app.presentation.navigation.NewConversationRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NewConversationUiState(
    val query: String = "",
    val results: List<Person> = emptyList(),
    val isSearching: Boolean = false,
    /** La recherche a abouti : sans résultat, l'écran dit que personne ne porte ce nom. */
    val searched: Boolean = false,
    val isOpening: Boolean = false,
)

/**
 * Nouvelle conversation (ADR-025) : recherche d'un destinataire sur son nom affiché ; chaque
 * résultat montre l'avatar, le nom, et le @handle de la chaîne s'il y en a une.
 */
@HiltViewModel
class NewConversationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messaging: MessagingUseCases,
) : ViewModel() {

    // Venue de « Envoyer à » (ADR-025, étape 2) : la vidéo part dès que la personne est choisie.
    private val shareVideoId = savedStateHandle.toRoute<NewConversationRoute>().shareVideoId

    private val _uiState = MutableStateFlow(NewConversationUiState())
    val uiState: StateFlow<NewConversationUiState> = _uiState.asStateFlow()

    private val _opened = Channel<String>(Channel.BUFFERED)
    /** Conversation à ouvrir, une fois créée ou retrouvée. */
    val opened: Flow<String> = _opened.receiveAsFlow()

    private val _errors = MutableSharedFlow<UiError>(extraBufferCapacity = 1)
    val errors: SharedFlow<UiError> = _errors.asSharedFlow()

    private var searchJob: Job? = null

    // La recherche part après une courte pause de frappe, pas à chaque lettre.
    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(results = emptyList(), isSearching = false, searched = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _uiState.update { it.copy(isSearching = true) }
            try {
                val results = messaging.repository.searchPeople(query.trim())
                _uiState.update { it.copy(results = results, isSearching = false, searched = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSearching = false) }
                _errors.tryEmit(e.toUiError())
            }
        }
    }

    fun open(person: Person) {
        if (_uiState.value.isOpening) return
        _uiState.update { it.copy(isOpening = true) }
        viewModelScope.launch {
            try {
                val conversation = messaging.repository.startConversation(person.id)
                if (shareVideoId != null) {
                    messaging.repository.sendMessage(conversation.id, content = "", videoId = shareVideoId)
                }
                _opened.send(conversation.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errors.tryEmit(e.toUiError())
            } finally {
                _uiState.update { it.copy(isOpening = false) }
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
