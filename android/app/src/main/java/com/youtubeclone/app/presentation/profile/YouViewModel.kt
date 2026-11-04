package com.youtubeclone.app.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.Channel
import com.youtubeclone.app.domain.model.PremiumStatus
import com.youtubeclone.app.domain.model.User
import com.youtubeclone.app.domain.usecase.CancelPremiumUseCase
import com.youtubeclone.app.domain.usecase.GetCurrentUserUseCase
import com.youtubeclone.app.domain.usecase.GetOwnChannelUseCase
import com.youtubeclone.app.domain.usecase.LogoutUseCase
import com.youtubeclone.app.domain.usecase.ObserveOwnChannelCreatedUseCase
import com.youtubeclone.app.domain.usecase.ObserveOwnChannelUpdatedUseCase
import com.youtubeclone.app.domain.usecase.ObservePremiumStatusUseCase
import com.youtubeclone.app.domain.usecase.ObserveSessionUseCase
import com.youtubeclone.app.presentation.common.UiError
import com.youtubeclone.app.presentation.common.toUiError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface YouUiState {
    data object Loading : YouUiState
    data object LoggedOut : YouUiState
    data class Error(val error: UiError) : YouUiState
    /** [channel] : chaîne de l'utilisateur, null s'il n'en a pas (ou si elle n'a pas pu être lue). */
    data class LoggedIn(val user: User, val channel: Channel?) : YouUiState
}

@HiltViewModel
class YouViewModel @Inject constructor(
    observeSession: ObserveSessionUseCase,
    private val getCurrentUser: GetCurrentUserUseCase,
    private val getOwnChannel: GetOwnChannelUseCase,
    private val logout: LogoutUseCase,
    observePremium: ObservePremiumStatusUseCase,
    private val cancelPremium: CancelPremiumUseCase,
    observeOwnChannelCreated: ObserveOwnChannelCreatedUseCase,
    observeOwnChannelUpdated: ObserveOwnChannelUpdatedUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<YouUiState>(YouUiState.Loading)
    val uiState: StateFlow<YouUiState> = _uiState.asStateFlow()

    /** Abonnement Premium (payant) ; null tant qu'il n'est pas connu. */
    val premium: StateFlow<PremiumStatus?> = observePremium()

    // Issue d'une annulation : message de confirmation ou d'erreur.
    private val _premiumMessages = MutableSharedFlow<UiError>(extraBufferCapacity = 1)
    val premiumMessages: SharedFlow<UiError> = _premiumMessages.asSharedFlow()
    private var cancelJob: Job? = null

    private var loadJob: Job? = null

    init {
        // Suit la session : connexion depuis un autre écran, déconnexion, ou expiration du refresh token.
        viewModelScope.launch {
            observeSession().collect { isLoggedIn ->
                if (isLoggedIn) load() else showLoggedOut()
            }
        }
        // Chaîne créée depuis l'application : l'en-tête montre aussitôt « Voir la chaîne ».
        viewModelScope.launch {
            observeOwnChannelCreated().collect { load() }
        }
        // Chaîne personnalisée (ADR-018) : le handle affiché dans l'en-tête suit.
        viewModelScope.launch {
            observeOwnChannelUpdated().collect { load() }
        }
    }

    fun load() {
        loadJob?.cancel()
        _uiState.value = YouUiState.Loading
        loadJob = viewModelScope.launch {
            // La chaîne est lue en parallèle du profil. Son échec ne bloque pas l'onglet :
            // le lien « Voir la chaîne » est simplement masqué.
            val channel = async {
                try {
                    getOwnChannel()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }
            _uiState.value = try {
                YouUiState.LoggedIn(getCurrentUser(), channel.await())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                channel.cancel()
                YouUiState.Error(e.toUiError())
            }
        }
    }

    /** Fin immédiate de l'abonnement, après confirmation dans l'écran. */
    fun onCancelPremium() {
        if (cancelJob?.isActive == true) return
        cancelJob = viewModelScope.launch {
            try {
                cancelPremium()
                _premiumMessages.tryEmit(UiError(R.string.premium_canceled))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _premiumMessages.tryEmit(e.toUiError())
            }
        }
    }

    fun onLogoutClick() {
        viewModelScope.launch { logout() }
    }

    private fun showLoggedOut() {
        loadJob?.cancel()
        _uiState.value = YouUiState.LoggedOut
    }
}
