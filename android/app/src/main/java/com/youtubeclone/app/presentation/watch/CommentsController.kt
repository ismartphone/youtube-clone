package com.youtubeclone.app.presentation.watch

import com.youtubeclone.app.domain.model.Comment
import com.youtubeclone.app.domain.usecase.GetCommentsUseCase
import com.youtubeclone.app.domain.usecase.PostCommentUseCase
import com.youtubeclone.app.presentation.common.UiError
import com.youtubeclone.app.presentation.common.toUiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommentsUiState(
    val comments: List<Comment> = emptyList(),
    /** Nombre de commentaires de la vidéo, y compris ceux pas encore chargés. */
    val total: Int = 0,
    val isLoading: Boolean = true,
    val error: UiError? = null,
    val isLoadingMore: Boolean = false,
    val draft: String = "",
    val isPosting: Boolean = false,
) {
    val hasMore: Boolean get() = comments.size < total
    val canPost: Boolean get() = draft.isNotBlank() && !isPosting
}

/**
 * Commentaires sous le lecteur : chargement par pages, publication, et ajout en direct
 * des commentaires diffusés par le serveur. Créé par le ViewModel avec son viewModelScope.
 */
class CommentsController(
    private val scope: CoroutineScope,
    private val videoId: String,
    private val getComments: GetCommentsUseCase,
    private val postComment: PostCommentUseCase,
) {
    private val _state = MutableStateFlow(CommentsUiState())
    val state: StateFlow<CommentsUiState> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<UiError>(extraBufferCapacity = 1)
    val errors: SharedFlow<UiError> = _errors.asSharedFlow()

    private var loadedPages = 0
    private var loadJob: Job? = null

    /** Charge (ou recharge, après une reconnexion) la première page. */
    fun loadFirstPage() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = it.comments.isEmpty(), error = null) }
        loadJob = scope.launch {
            try {
                val page = getComments(videoId)
                loadedPages = 1
                _state.update { it.copy(comments = page.items, total = page.total, isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Liste déjà affichée : on la garde plutôt que de la remplacer par une erreur.
                _state.update { it.copy(isLoading = false, error = if (it.comments.isEmpty()) e.toUiError() else null) }
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.isLoadingMore || current.isLoading || !current.hasMore) return
        _state.update { it.copy(isLoadingMore = true) }
        scope.launch {
            try {
                val page = getComments(videoId, page = loadedPages + 1)
                loadedPages += 1
                // Des commentaires arrivés entre-temps décalent les pages : on ignore les doublons.
                _state.update { state ->
                    val known = state.comments.mapTo(HashSet()) { it.id }
                    state.copy(
                        comments = state.comments + page.items.filterNot { it.id in known },
                        total = page.total,
                        isLoadingMore = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingMore = false) }
                _errors.tryEmit(e.toUiError())
            }
        }
    }

    fun onDraftChange(text: String) {
        _state.update { it.copy(draft = text.take(PostCommentUseCase.MAX_LENGTH)) }
    }

    /** À n'appeler qu'avec une session ouverte : sinon l'écran redirige vers la connexion. */
    fun post() {
        val current = _state.value
        if (!current.canPost) return
        _state.update { it.copy(isPosting = true) }
        scope.launch {
            try {
                val comment = postComment(videoId, current.draft)
                add(comment)
                _state.update { it.copy(draft = "", isPosting = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Le brouillon est gardé, pour pouvoir réessayer.
                _state.update { it.copy(isPosting = false) }
                _errors.tryEmit(e.toUiError())
            }
        }
    }

    /** Commentaire diffusé par le serveur (WebSocket), le nôtre compris. */
    fun onCommentReceived(comment: Comment) = add(comment)

    // Notre propre commentaire arrive deux fois (réponse du POST et diffusion) : une seule entrée.
    private fun add(comment: Comment) {
        _state.update { state ->
            if (state.comments.any { it.id == comment.id }) {
                state
            } else {
                state.copy(comments = listOf(comment) + state.comments, total = state.total + 1)
            }
        }
    }
}
