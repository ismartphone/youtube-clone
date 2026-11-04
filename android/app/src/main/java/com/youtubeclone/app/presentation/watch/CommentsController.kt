package com.youtubeclone.app.presentation.watch

import com.youtubeclone.app.domain.model.Channel
import com.youtubeclone.app.domain.model.Comment
import com.youtubeclone.app.domain.model.CommentReaction
import com.youtubeclone.app.domain.model.CommentSort
import com.youtubeclone.app.domain.usecase.CommentUseCases
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

/** Réponses d'un commentaire (ADR-023), chargées à la demande, 10 par page. */
data class RepliesUiState(
    val items: List<Comment> = emptyList(),
    /** Nombre de réponses du fil, y compris celles pas encore chargées. */
    val total: Int = 0,
    val expanded: Boolean = false,
    val isLoading: Boolean = false,
    val loadedPages: Int = 0,
) {
    val hasMore: Boolean get() = items.size < total
}

/**
 * Réponse en cours de saisie, sous le fil [threadId]. [mention] préremplit le champ : @handle de
 * la personne à qui on répond, ou son nom affiché si elle n'a pas de chaîne (ADR-023).
 */
data class ReplyTarget(val threadId: String, val mention: String)

data class CommentsUiState(
    val comments: List<Comment> = emptyList(),
    /** Nombre de commentaires et de réponses de la vidéo, y compris ceux pas encore chargés. */
    val total: Int = 0,
    val isLoading: Boolean = true,
    val error: UiError? = null,
    val isLoadingMore: Boolean = false,
    val draft: String = "",
    val isPosting: Boolean = false,
    /** Commentaires arrivés pendant qu'on regarde (en direct ou publiés ici) : ils apparaissent animés. */
    val arrivedCommentIds: Set<String> = emptySet(),
    val sort: CommentSort = CommentSort.Top,
    /** Fils de réponses déjà ouverts, par commentaire de premier niveau. */
    val replies: Map<String, RepliesUiState> = emptyMap(),
    val replyTarget: ReplyTarget? = null,
    val replyDraft: String = "",
    val isPostingReply: Boolean = false,
    /** Commentaires dont un avis ou un cœur est en cours d'envoi : leurs boutons attendent. */
    val pendingIds: Set<String> = emptySet(),
    /** Chaîne de la vidéo : son avatar accompagne le cœur du créateur. */
    val creator: Channel? = null,
    /** L'utilisateur est le propriétaire de la chaîne : il peut mettre un cœur. */
    val canHeart: Boolean = false,
) {
    val hasMore: Boolean get() = commentCountLoaded < total
    val canPost: Boolean get() = draft.isNotBlank() && !isPosting
    val canPostReply: Boolean get() = replyDraft.isNotBlank() && !isPostingReply

    // Les réponses comptent dans total : la liste continue tant qu'il reste des commentaires
    // de premier niveau à charger.
    private val commentCountLoaded: Int get() = comments.size + comments.sumOf { it.replyCount }
}

/**
 * Commentaires sous le lecteur : chargement par pages et tri, réponses, avis, cœur du créateur,
 * publication, et ajout en direct de ce que diffuse le serveur (ADR-023). Créé par le ViewModel
 * avec son viewModelScope.
 */
class CommentsController(
    private val scope: CoroutineScope,
    private val videoId: String,
    private val useCases: CommentUseCases,
) {
    private val _state = MutableStateFlow(CommentsUiState())
    val state: StateFlow<CommentsUiState> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<UiError>(extraBufferCapacity = 1)
    val errors: SharedFlow<UiError> = _errors.asSharedFlow()

    private var loadedPages = 0
    private var loadJob: Job? = null

    // Réponses déjà comptées : la nôtre arrive deux fois (réponse du POST et diffusion).
    private val countedReplyIds = HashSet<String>()

    /** Chaîne de la vidéo, et droit de l'utilisateur à y mettre un cœur (propriétaire). */
    fun setCreator(channel: Channel, isOwner: Boolean) {
        _state.update { it.copy(creator = channel, canHeart = isOwner) }
    }

    /** Charge (ou recharge, après une reconnexion ou une connexion) la première page. */
    fun loadFirstPage() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = it.comments.isEmpty(), error = null) }
        val sort = _state.value.sort
        loadJob = scope.launch {
            try {
                val page = useCases.getComments(videoId, sort = sort)
                loadedPages = 1
                _state.update { it.copy(comments = page.items, total = page.total, isLoading = false) }
                // Les fils ouverts sont relus : avis de l'utilisateur et réponses manquées.
                _state.value.replies.filterValues { it.expanded }.keys.forEach(::reloadReplies)
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
                val page = useCases.getComments(videoId, page = loadedPages + 1, sort = current.sort)
                loadedPages += 1
                // Des commentaires arrivés entre-temps, ou montés dans le tri « Top », décalent les
                // pages : les doublons sont ignorés (limite assumée par l'ADR-023).
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

    /** « Top commentaires » ou « Les plus récents » : la liste repart de la première page. */
    fun setSort(sort: CommentSort) {
        if (sort == _state.value.sort) return
        _state.update { it.copy(sort = sort, comments = emptyList(), replies = emptyMap(), replyTarget = null) }
        loadFirstPage()
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
                val comment = useCases.postComment(videoId, current.draft)
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

    /** « N réponses » : déplie le fil (chargé à la première ouverture), ou le replie. */
    fun toggleReplies(comment: Comment) {
        val thread = _state.value.replies[comment.id]
        if (thread?.expanded == true) {
            updateThread(comment.id) { it.copy(expanded = false) }
            return
        }
        updateThread(comment.id) { (if (thread == null) RepliesUiState(total = comment.replyCount) else it).copy(expanded = true) }
        if (thread == null || thread.loadedPages == 0) loadReplies(comment.id, page = 1)
    }

    /** « Afficher plus de réponses » : 10 de plus. */
    fun loadMoreReplies(threadId: String) {
        val thread = _state.value.replies[threadId] ?: return
        if (thread.isLoading || !thread.hasMore) return
        loadReplies(threadId, page = thread.loadedPages + 1)
    }

    /**
     * « Répondre » sous un commentaire ou une réponse : le champ s'ouvre sous le fil, prérempli
     * avec le @handle de l'auteur, ou son nom affiché suivi d'une espace s'il n'a pas de chaîne.
     * À n'appeler qu'avec une session ouverte.
     */
    fun startReply(comment: Comment) {
        val threadId = comment.parentId ?: comment.id
        val mention = comment.author.handle?.let { "@$it " } ?: "${comment.author.displayName} "
        _state.update { it.copy(replyTarget = ReplyTarget(threadId, mention), replyDraft = mention) }
        // Le fil s'ouvre : la réponse publiée y apparaît, sous les autres.
        val root = _state.value.comments.firstOrNull { it.id == threadId }
        if (root != null && _state.value.replies[threadId]?.expanded != true) toggleReplies(root)
    }

    fun cancelReply() {
        _state.update { it.copy(replyTarget = null, replyDraft = "") }
    }

    fun onReplyDraftChange(text: String) {
        _state.update { it.copy(replyDraft = text.take(PostCommentUseCase.MAX_LENGTH)) }
    }

    fun postReply() {
        val current = _state.value
        val target = current.replyTarget ?: return
        if (!current.canPostReply) return
        _state.update { it.copy(isPostingReply = true) }
        scope.launch {
            try {
                val reply = useCases.postComment(videoId, current.replyDraft, parentId = target.threadId)
                addReply(reply)
                _state.update { it.copy(replyTarget = null, replyDraft = "", isPostingReply = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isPostingReply = false) }
                _errors.tryEmit(e.toUiError())
            }
        }
    }

    /**
     * Pouce levé ou baissé sur un commentaire ou une réponse : un second appui retire l'avis.
     * À n'appeler qu'avec une session ouverte. Pas de temps réel : seul ce compteur change.
     */
    fun react(comment: Comment, reaction: CommentReaction) {
        if (comment.id in _state.value.pendingIds) return
        val on = comment.reaction != reaction
        runPending(comment.id) {
            val status = useCases.react(comment.id, reaction, on)
            updateComment(comment.id) { it.copy(likeCount = status.likeCount, reaction = status.reaction) }
        }
    }

    /** Cœur du créateur, posé ou retiré. Réservé au propriétaire de la chaîne. */
    fun toggleHeart(comment: Comment) {
        if (!_state.value.canHeart || comment.id in _state.value.pendingIds) return
        val hearted = !comment.heartedByCreator
        runPending(comment.id) {
            useCases.heart(comment.id, hearted)
            updateComment(comment.id) { it.copy(heartedByCreator = hearted) }
        }
    }

    /** Commentaire diffusé par le serveur (WebSocket), le nôtre compris. */
    fun onCommentReceived(comment: Comment) = add(comment)

    /** Réponse diffusée par le serveur (comment:reply), la nôtre comprise. */
    fun onReplyReceived(reply: Comment) = addReply(reply)

    // Notre propre commentaire arrive deux fois (réponse du POST et diffusion) : une seule entrée.
    private fun add(comment: Comment) {
        _state.update { state ->
            if (state.comments.any { it.id == comment.id }) {
                state
            } else {
                state.copy(
                    comments = listOf(comment) + state.comments,
                    total = state.total + 1,
                    arrivedCommentIds = state.arrivedCommentIds + comment.id,
                )
            }
        }
    }

    // Une réponse compte une fois dans le total de la vidéo et dans « N réponses » ; elle n'est
    // ajoutée à la liste que si le fil est déjà chargé (sinon il la lira à l'ouverture).
    private fun addReply(reply: Comment) {
        val threadId = reply.parentId ?: return
        val isNew = countedReplyIds.add(reply.id)
        _state.update { state ->
            val thread = state.replies[threadId]
            val alreadyListed = thread?.items?.any { it.id == reply.id } == true
            val counted = isNew && !alreadyListed
            state.copy(
                total = if (counted) state.total + 1 else state.total,
                comments = if (counted) {
                    state.comments.map { if (it.id == threadId) it.copy(replyCount = it.replyCount + 1) else it }
                } else {
                    state.comments
                },
                replies = if (thread != null && thread.loadedPages > 0 && !alreadyListed) {
                    state.replies + (
                        threadId to thread.copy(
                            items = thread.items + reply,
                            total = if (counted) thread.total + 1 else thread.total,
                        )
                    )
                } else {
                    state.replies
                },
                arrivedCommentIds = state.arrivedCommentIds + reply.id,
            )
        }
    }

    private fun loadReplies(threadId: String, page: Int) {
        updateThread(threadId) { it.copy(isLoading = true) }
        scope.launch {
            try {
                val result = useCases.getReplies(threadId, page)
                result.items.forEach { countedReplyIds.add(it.id) }
                updateThread(threadId) { thread ->
                    // Première page : elle remplace ; les suivantes s'ajoutent, sans doublon.
                    val base = if (page == 1) emptyList() else thread.items
                    val known = base.mapTo(HashSet()) { it.id }
                    thread.copy(
                        items = base + result.items.filterNot { it.id in known },
                        total = result.total,
                        loadedPages = page,
                        isLoading = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateThread(threadId) { it.copy(isLoading = false) }
                _errors.tryEmit(e.toUiError())
            }
        }
    }

    private fun reloadReplies(threadId: String) = loadReplies(threadId, page = 1)

    private fun updateThread(threadId: String, change: (RepliesUiState) -> RepliesUiState) {
        _state.update { state ->
            state.copy(replies = state.replies + (threadId to change(state.replies[threadId] ?: RepliesUiState())))
        }
    }

    // Un commentaire peut être dans la liste ou dans un fil de réponses.
    private fun updateComment(id: String, change: (Comment) -> Comment) {
        _state.update { state ->
            state.copy(
                comments = state.comments.map { if (it.id == id) change(it) else it },
                replies = state.replies.mapValues { (_, thread) ->
                    thread.copy(items = thread.items.map { if (it.id == id) change(it) else it })
                },
            )
        }
    }

    private fun runPending(id: String, action: suspend () -> Unit) {
        _state.update { it.copy(pendingIds = it.pendingIds + id) }
        scope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errors.tryEmit(e.toUiError())
            } finally {
                _state.update { it.copy(pendingIds = it.pendingIds - id) }
            }
        }
    }
}
