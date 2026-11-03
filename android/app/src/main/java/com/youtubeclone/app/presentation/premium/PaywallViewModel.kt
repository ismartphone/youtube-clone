package com.youtubeclone.app.presentation.premium

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeclone.app.domain.analytics.Analytics
import com.youtubeclone.app.domain.analytics.AnalyticsEvent
import com.youtubeclone.app.domain.model.DataError
import com.youtubeclone.app.domain.model.PremiumStatus
import com.youtubeclone.app.domain.usecase.ConfirmPremiumPurchaseUseCase
import com.youtubeclone.app.domain.usecase.ObservePremiumStatusUseCase
import com.youtubeclone.app.domain.usecase.ObserveSessionUseCase
import com.youtubeclone.app.domain.usecase.RefreshPremiumStatusUseCase
import com.youtubeclone.app.domain.usecase.StartPremiumCheckoutUseCase
import com.youtubeclone.app.presentation.common.UiError
import com.youtubeclone.app.presentation.common.toUiError
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

data class PaywallUiState(
    val isLoggedIn: Boolean = false,
    /** Déjà membre : l'écran le dit au lieu de proposer l'achat. */
    val isAlreadyPremium: Boolean = false,
    /** Appel au serveur en cours (début d'achat ou confirmation). */
    val isBusy: Boolean = false,
    /** Jeton émis par le serveur : la feuille de paiement simulée est ouverte. */
    val checkoutToken: String? = null,
    /** L'achat est confirmé par le serveur : l'écran se ferme. */
    val isPurchased: Boolean = false,
)

@HiltViewModel
class PaywallViewModel @Inject constructor(
    observeSession: ObserveSessionUseCase,
    observePremium: ObservePremiumStatusUseCase,
    private val refreshPremium: RefreshPremiumStatusUseCase,
    private val startCheckout: StartPremiumCheckoutUseCase,
    private val confirmPurchase: ConfirmPremiumPurchaseUseCase,
    private val analytics: Analytics,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaywallUiState())
    val uiState: StateFlow<PaywallUiState> = _uiState.asStateFlow()

    private val _errors = MutableSharedFlow<UiError>(extraBufferCapacity = 1)
    val errors: SharedFlow<UiError> = _errors.asSharedFlow()

    init {
        analytics.track(AnalyticsEvent.PaywallViewed)
        viewModelScope.launch {
            observeSession().collect { loggedIn -> _uiState.update { it.copy(isLoggedIn = loggedIn) } }
        }
        viewModelScope.launch {
            observePremium().collect { status ->
                // Pendant l'achat, l'écran passe directement à « confirmé », pas à « déjà membre ».
                if (!_uiState.value.isPurchased) {
                    _uiState.update { it.copy(isAlreadyPremium = status?.isPremium == true) }
                }
            }
        }
    }

    /** Étape 1 (ADR-012) : le serveur émet un jeton, la feuille de paiement simulée s'ouvre. */
    fun startPurchase() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        analytics.track(AnalyticsEvent.PurchaseStarted)
        viewModelScope.launch {
            try {
                val token = startCheckout()
                _uiState.update { it.copy(isBusy = false, checkoutToken = token) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isBusy = false) }
                // 409 : déjà membre (achat fait depuis un autre appareil, par exemple).
                if (e is DataError.Server && e.code == HTTP_CONFLICT) refreshQuietly()
                _errors.tryEmit(e.toUiError())
            }
        }
    }

    /** Étape 2 : le serveur vérifie le jeton et active l'abonnement. */
    fun confirm() {
        val token = _uiState.value.checkoutToken ?: return
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                val status: PremiumStatus = confirmPurchase(token)
                if (status.isPremium) analytics.track(AnalyticsEvent.PurchaseConfirmed)
                _uiState.update {
                    it.copy(isBusy = false, checkoutToken = null, isPurchased = status.isPremium)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Jeton refusé ou expiré : la feuille se ferme, l'utilisateur peut recommencer.
                _uiState.update { it.copy(isBusy = false, checkoutToken = null) }
                _errors.tryEmit(e.toUiError())
            }
        }
    }

    /** Feuille fermée sans confirmer : le jeton expirera de lui-même côté serveur. */
    fun dismissCheckout() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(checkoutToken = null) }
    }

    // Écran quitté sans achat confirmé : abandon du paywall. Un membre déjà Premium n'est pas compté.
    override fun onCleared() {
        val state = _uiState.value
        if (!state.isPurchased && !state.isAlreadyPremium) analytics.track(AnalyticsEvent.PaywallDismissed)
    }

    private suspend fun refreshQuietly() {
        try {
            refreshPremium()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val HTTP_CONFLICT = 409
    }
}
