package com.youtubeclone.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeclone.app.domain.repository.MessagingRepository
import com.youtubeclone.app.domain.usecase.ObserveAutoplayUseCase
import com.youtubeclone.app.domain.usecase.ObserveOnboardingFeedbackUseCase
import com.youtubeclone.app.domain.usecase.ObserveSessionUseCase
import com.youtubeclone.app.domain.usecase.ObserveShareUsageStatsUseCase
import com.youtubeclone.app.domain.usecase.SetAutoplayUseCase
import com.youtubeclone.app.domain.usecase.SetOnboardingFeedbackUseCase
import com.youtubeclone.app.domain.usecase.SetShareUsageStatsUseCase
import com.youtubeclone.app.presentation.common.UiError
import com.youtubeclone.app.presentation.common.toUiError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeShareUsageStats: ObserveShareUsageStatsUseCase,
    private val setShareUsageStats: SetShareUsageStatsUseCase,
    observeAutoplay: ObserveAutoplayUseCase,
    private val setAutoplay: SetAutoplayUseCase,
    observeOnboardingFeedback: ObserveOnboardingFeedbackUseCase,
    private val setOnboardingFeedback: SetOnboardingFeedbackUseCase,
    observeSession: ObserveSessionUseCase,
    private val messaging: MessagingRepository,
) : ViewModel() {

    private val _messageNotifications = MutableStateFlow<Boolean?>(null)

    /**
     * « Notifications de messages » (ADR-025, étape 3), stocké côté serveur. null pour un visiteur
     * ou tant qu'il n'est pas lu : l'interrupteur n'est pas affiché.
     */
    val messageNotifications: StateFlow<Boolean?> = _messageNotifications.asStateFlow()

    private val _errors = MutableSharedFlow<UiError>(extraBufferCapacity = 1)
    val errors: SharedFlow<UiError> = _errors.asSharedFlow()

    init {
        viewModelScope.launch {
            observeSession().collect { loggedIn ->
                _messageNotifications.value = if (loggedIn) runCatching { messaging.getMessageNotifications() }.getOrNull() else null
            }
        }
    }

    // Bascule aussitôt à l'écran ; revient en arrière si le serveur refuse.
    fun onMessageNotificationsChange(enabled: Boolean) {
        val previous = _messageNotifications.value ?: return
        _messageNotifications.value = enabled
        viewModelScope.launch {
            try {
                _messageNotifications.value = messaging.setMessageNotifications(enabled)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messageNotifications.value = previous
                _errors.tryEmit(e.toUiError())
            }
        }
    }

    /** null tant que la préférence n'est pas lue. */
    val autoplay: StateFlow<Boolean?> = observeAutoplay()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onAutoplayChange(enabled: Boolean) {
        viewModelScope.launch { setAutoplay(enabled) }
    }

    /** « Sons et vibrations » du parcours d'accueil (ADR-028) ; null tant que la préférence n'est pas lue. */
    val onboardingFeedback: StateFlow<Boolean?> = observeOnboardingFeedback()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onOnboardingFeedbackChange(enabled: Boolean) {
        viewModelScope.launch { setOnboardingFeedback(enabled) }
    }

    /** null tant que la préférence n'est pas lue : l'interrupteur n'est pas encore affiché. */
    val shareUsageStats: StateFlow<Boolean?> = observeShareUsageStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onShareUsageStatsChange(enabled: Boolean) {
        viewModelScope.launch { setShareUsageStats(enabled) }
    }
}
