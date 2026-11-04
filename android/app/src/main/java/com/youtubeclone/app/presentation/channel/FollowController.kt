package com.youtubeclone.app.presentation.channel

import com.youtubeclone.app.domain.model.FollowStatus
import com.youtubeclone.app.domain.model.NotificationLevel
import com.youtubeclone.app.domain.usecase.ObserveFollowChangesUseCase
import com.youtubeclone.app.domain.usecase.SetFollowingUseCase
import com.youtubeclone.app.domain.usecase.SetNotificationLevelUseCase
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
 * [notificationLevel] : cloche de l'abonnement (ADR-020), null sans abonnement.
 */
data class FollowUiState(
    val followerCount: Int,
    val isFollowing: Boolean,
    val canFollow: Boolean = true,
    val isPending: Boolean = false,
    val notificationLevel: NotificationLevel? = null,
) {
    val status: FollowStatus get() = FollowStatus(followerCount, isFollowing, notificationLevel)
}

/**
 * Logique du bouton S'abonner, partagée par la page de chaîne et l'écran de lecture.
 * Créée par un ViewModel avec son viewModelScope. Le bouton ne change qu'après la réponse
 * du serveur, et suit aussi les abonnements faits depuis un autre écran.
 * [setNotificationLevel] : null là où la cloche n'est pas proposée (onglet Shorts, ADR-020).
 */
class FollowController(
    private val scope: CoroutineScope,
    private val setFollowing: SetFollowingUseCase,
    observeFollowChanges: ObserveFollowChangesUseCase,
    private val setNotificationLevel: SetNotificationLevelUseCase? = null,
) {
    private val _state = MutableStateFlow<FollowUiState?>(null)

    /** Null tant que la chaîne n'est pas connue. */
    val state: StateFlow<FollowUiState?> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<UiError>(extraBufferCapacity = 1)
    val errors: SharedFlow<UiError> = _errors.asSharedFlow()

    // Une cloche vient d'être activée par l'utilisateur (abonnement, ou Toutes / Personnalisées) :
    // l'écran demande alors l'autorisation d'afficher des notifications (ADR-020).
    private val _bellActivated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val bellActivated: SharedFlow<Unit> = _bellActivated.asSharedFlow()

    private var channelId: String? = null

    init {
        scope.launch {
            observeFollowChanges().collect { change ->
                if (change.channelId == channelId) {
                    _state.update {
                        it?.copy(
                            followerCount = change.status.followerCount,
                            isFollowing = change.status.isFollowing,
                            notificationLevel = change.status.notificationLevel,
                        )
                    }
                }
            }
        }
    }

    fun show(channelId: String, status: FollowStatus, canFollow: Boolean) {
        this.channelId = channelId
        _state.value = FollowUiState(status.followerCount, status.isFollowing, canFollow, notificationLevel = status.notificationLevel)
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
                _state.value = current.copy(
                    followerCount = status.followerCount,
                    isFollowing = status.isFollowing,
                    isPending = false,
                    notificationLevel = status.notificationLevel,
                )
                if (status.isFollowing && setNotificationLevel != null) _bellActivated.tryEmit(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it?.copy(isPending = false) }
                _errors.tryEmit(e.toUiError())
            }
        }
    }

    /** Change la cloche ; le bouton suit la réponse du serveur. */
    fun selectNotificationLevel(level: NotificationLevel) {
        val id = channelId ?: return
        val setLevel = setNotificationLevel ?: return
        val current = _state.value ?: return
        if (current.isPending || !current.isFollowing || current.notificationLevel == level) return
        _state.value = current.copy(isPending = true)
        scope.launch {
            try {
                val status = setLevel(id, level, current.status)
                _state.value = current.copy(notificationLevel = status.notificationLevel)
                if (level != NotificationLevel.None) _bellActivated.tryEmit(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it?.copy(isPending = false) }
                _errors.tryEmit(e.toUiError())
            }
        }
    }
}
