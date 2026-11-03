package com.youtubeclone.app.presentation.premium

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.analytics.Analytics
import com.youtubeclone.app.domain.analytics.AnalyticsEvent
import com.youtubeclone.app.domain.analytics.Experiments
import com.youtubeclone.app.domain.analytics.PaywallVariant
import com.youtubeclone.app.domain.model.DataError
import com.youtubeclone.app.domain.model.PaymentConfig
import com.youtubeclone.app.domain.model.PaymentMode
import com.youtubeclone.app.domain.model.PremiumStatus
import com.youtubeclone.app.domain.usecase.ConfirmPremiumPurchaseUseCase
import com.youtubeclone.app.domain.usecase.GetPaymentConfigUseCase
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

/** Paiement Stripe prêt à être présenté dans la PaymentSheet. */
data class StripePayment(
    val publishableKey: String,
    val clientSecret: String,
)

/** Issue de la PaymentSheet Stripe, traduite par l'écran pour ne pas exposer le SDK ici. */
sealed interface PaymentOutcome {
    data object Completed : PaymentOutcome
    data object Canceled : PaymentOutcome
    data object Failed : PaymentOutcome
}

data class PaywallUiState(
    /** Variante du test A/B, fixée à l'ouverture de l'écran. */
    val variant: PaywallVariant = PaywallVariant.BENEFITS,
    val isLoggedIn: Boolean = false,
    /** Mode de paiement donné par le serveur ; null tant qu'il n'est pas connu. */
    val paymentMode: PaymentMode? = null,
    /** Déjà membre : l'écran le dit au lieu de proposer l'achat. */
    val isAlreadyPremium: Boolean = false,
    /** Achat en cours : appel au serveur, ou feuille de paiement ouverte. */
    val isBusy: Boolean = false,
    /** Mode simulé : jeton émis par le serveur, la feuille de paiement simulée est ouverte. */
    val checkoutToken: String? = null,
    /** Mode Stripe : paiement à présenter dans la PaymentSheet, une seule fois. */
    val stripePayment: StripePayment? = null,
    /** L'achat est confirmé par le serveur : l'écran se ferme. */
    val isPurchased: Boolean = false,
)

@HiltViewModel
class PaywallViewModel @Inject constructor(
    observeSession: ObserveSessionUseCase,
    observePremium: ObservePremiumStatusUseCase,
    private val refreshPremium: RefreshPremiumStatusUseCase,
    private val getPaymentConfig: GetPaymentConfigUseCase,
    private val startCheckout: StartPremiumCheckoutUseCase,
    private val confirmPurchase: ConfirmPremiumPurchaseUseCase,
    private val analytics: Analytics,
    experiments: Experiments,
) : ViewModel() {

    private val variant = experiments.paywallVariant()

    private val _uiState = MutableStateFlow(PaywallUiState(variant = variant))
    val uiState: StateFlow<PaywallUiState> = _uiState.asStateFlow()

    private val _errors = MutableSharedFlow<UiError>(extraBufferCapacity = 1)
    val errors: SharedFlow<UiError> = _errors.asSharedFlow()

    private var paymentConfig: PaymentConfig? = null

    // Référence du paiement Stripe en cours, renvoyée au serveur une fois la PaymentSheet réglée.
    private var pendingStripeToken: String? = null

    init {
        analytics.track(AnalyticsEvent.PaywallViewed(variant))
        viewModelScope.launch {
            observeSession().collect { loggedIn ->
                _uiState.update { it.copy(isLoggedIn = loggedIn) }
                // La configuration exige une session : elle est lue dès que l'utilisateur est connecté.
                if (loggedIn && paymentConfig == null) loadPaymentConfigQuietly()
            }
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

    /**
     * Étape 1 (ADR-014) : le serveur démarre le paiement. En mode Stripe, la PaymentSheet s'ouvre ;
     * en mode simulé (repli), la feuille de paiement simulée.
     */
    fun startPurchase() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        analytics.track(AnalyticsEvent.PurchaseStarted(variant))
        viewModelScope.launch {
            try {
                val checkout = startCheckout()
                when (checkout.mode) {
                    PaymentMode.SIMULATED -> _uiState.update {
                        it.copy(isBusy = false, paymentMode = checkout.mode, checkoutToken = checkout.purchaseToken)
                    }
                    PaymentMode.STRIPE -> {
                        // Configuration pas encore lue (réseau lent à l'ouverture) : on la relit.
                        val publishableKey = (paymentConfig ?: loadPaymentConfig()).publishableKey
                        val clientSecret = checkout.clientSecret
                        if (publishableKey == null || clientSecret == null) {
                            _uiState.update { it.copy(isBusy = false) }
                            _errors.tryEmit(UiError(R.string.premium_payment_failed))
                            return@launch
                        }
                        pendingStripeToken = checkout.purchaseToken
                        _uiState.update { it.copy(stripePayment = StripePayment(publishableKey, clientSecret)) }
                    }
                }
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

    /** La PaymentSheet est affichée : elle ne doit pas être présentée une seconde fois. */
    fun onStripeSheetShown() {
        _uiState.update { it.copy(stripePayment = null) }
    }

    /**
     * Fin de la PaymentSheet. Réglée : le serveur vérifie le paiement auprès de Stripe avant
     * d'accorder l'abonnement. Annulée ou échouée : rien n'est accordé.
     */
    fun onPaymentOutcome(outcome: PaymentOutcome) {
        val token = pendingStripeToken
        pendingStripeToken = null
        when (outcome) {
            PaymentOutcome.Completed -> if (token != null) confirmWith(token)
            PaymentOutcome.Canceled -> {
                _uiState.update { it.copy(isBusy = false) }
                _errors.tryEmit(UiError(R.string.premium_payment_canceled))
            }
            PaymentOutcome.Failed -> {
                _uiState.update { it.copy(isBusy = false) }
                _errors.tryEmit(UiError(R.string.premium_payment_failed))
            }
        }
    }

    /** Mode simulé, étape 2 : le serveur vérifie le jeton et active l'abonnement. */
    fun confirm() {
        val token = _uiState.value.checkoutToken ?: return
        if (_uiState.value.isBusy) return
        confirmWith(token)
    }

    /** Feuille simulée fermée sans confirmer : le jeton expirera de lui-même côté serveur. */
    fun dismissCheckout() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(checkoutToken = null) }
    }

    private fun confirmWith(token: String) {
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                val status: PremiumStatus = confirmPurchase(token)
                if (status.isPremium) analytics.track(AnalyticsEvent.PurchaseConfirmed(variant))
                _uiState.update {
                    it.copy(isBusy = false, checkoutToken = null, isPurchased = status.isPremium)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Paiement refusé ou jeton expiré : la feuille se ferme, l'utilisateur peut recommencer.
                _uiState.update { it.copy(isBusy = false, checkoutToken = null) }
                _errors.tryEmit(e.toUiError())
            }
        }
    }

    private suspend fun loadPaymentConfig(): PaymentConfig =
        getPaymentConfig().also { config ->
            paymentConfig = config
            _uiState.update { it.copy(paymentMode = config.mode) }
        }

    // À l'ouverture : un échec n'empêche rien, la configuration est relue au moment de payer.
    private suspend fun loadPaymentConfigQuietly() {
        try {
            loadPaymentConfig()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private suspend fun refreshQuietly() {
        try {
            refreshPremium()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    // Écran quitté sans achat confirmé : abandon du paywall. Un membre déjà Premium n'est pas compté.
    override fun onCleared() {
        val state = _uiState.value
        if (!state.isPurchased && !state.isAlreadyPremium) analytics.track(AnalyticsEvent.PaywallDismissed(variant))
    }

    private companion object {
        const val HTTP_CONFLICT = 409
    }
}
