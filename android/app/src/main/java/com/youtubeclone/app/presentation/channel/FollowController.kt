package com.youtubeclone.app.presentation.channel

import com.youtubeclone.app.domain.model.FollowStatus
import com.youtubeclone.app.domain.usecase.ObserveFollowChangesUseCase
import com.youtubeclone.app.domain.usecase.SetFollowingUseCase
import com.youtubeclone.app.presentation.common.UiError
import com.youtubeclone.app.presentation.common.toUiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * État du bouton S'abonner : [isPending] pendant l'appel au serveur, pour éviter les doubles clics.
 * [canFollow] vaut false sur sa propre chaîne : le bouton est masqué, le compteur reste affiché.
 */
data class FollowUiState(
    val followerCount: Int,
    val isFollowing: Boolean,
    val canFollow: Boolean = true,
    val isPending: Boolean = false,
)

/**
 * Logique du bouton S'abonner, partagée par la page de chaîne et l'écran de lecture.
 * Créée par un ViewModel avec son viewModelScope. Le bouton ne change qu'après la réponse
 * du serveur, et suit aussi les abonnements faits depuis un autre écran.
 */
class FollowController(
    private val scope: CoroutineScope,
    private val setFollowing: SetFollowingUseCase,
    observeFollowChanges: ObserveFollowChangesUseCase,
) {
    private val _state = MutableStateFlow<FollowUiState?>(null)

    /** Null tant que la chaîne n'est pas connue. */
    val state: StateFlow<FollowUiState?> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<UiError>(extraBufferCapacity = 1)
    val errors: SharedFlow<UiError> = _errors.asSharedFlow()

    private var channelId: String? = null

    init {
        scope.launch {
            observeFollowChanges().collect { change ->
                if (change.channelId == channelId) {
                    _state.update { it?.copy(followerCount = change.status.followerCount, isFollowing = change.status.isFollowing) }
                }
            }
        }
    }

    fun show(channelId: String, status: FollowStatus, canFollow: Boolean) {
        this.channelId = channelId
        _state.value = FollowUiState(status.followerCount, status.isFollowing, canFollow)
    }

    /** À n'appeler qu'avec une session ouverte : sinon l'écran redirige vers la connexion. */
    fun toggle() {
        val id = channelId ?: return
        val current = _state.value ?: return
        if (current.isPending || !current.canFollow) return
        _state.value = current.copy(isPending = true)
        scope.launch {
            try {
                val status = setFollowing(id, follow = !current.isFollowing)
                _state.value = current.copy(followerCount = status.followerCount, isFollowing = status.isFollowing)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it?.copy(isPending = false) }
                _errors.tryEmit(e.toUiError())
            }
        }
    }
}
