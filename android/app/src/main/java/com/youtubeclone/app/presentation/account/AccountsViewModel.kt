package com.youtubeclone.app.presentation.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeclone.app.domain.model.Account
import com.youtubeclone.app.domain.usecase.ObserveAccountsUseCase
import com.youtubeclone.app.domain.usecase.ObserveActiveAccountUseCase
import com.youtubeclone.app.domain.usecase.ObservePremiumStatusUseCase
import com.youtubeclone.app.domain.usecase.RefreshAccountProfileUseCase
import com.youtubeclone.app.domain.usecase.RemoveAccountUseCase
import com.youtubeclone.app.domain.usecase.SwitchAccountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Ce que la feuille des comptes demande à l'ossature de navigation. */
sealed interface AccountsEvent {
    /** Session expirée : connexion avec l'email prérempli (ADR-022, décision 2). */
    data class OpenLogin(val email: String) : AccountsEvent
}

/**
 * Comptes enregistrés sur l'appareil et bascule (ADR-022). Partagé par toute l'application :
 * la feuille s'ouvre depuis le fil et depuis Vous, et un changement de compte vide la navigation.
 */
@HiltViewModel
class AccountsViewModel @Inject constructor(
    observeAccounts: ObserveAccountsUseCase,
    observeActiveAccount: ObserveActiveAccountUseCase,
    private val switchAccount: SwitchAccountUseCase,
    private val removeAccount: RemoveAccountUseCase,
    refreshProfile: RefreshAccountProfileUseCase,
    observePremium: ObservePremiumStatusUseCase,
) : ViewModel() {

    /** Le compte actif est Premium : ses miniatures Premium s'affichent nettes (ADR-026). */
    val isPremium: StateFlow<Boolean> = observePremium()
        .map { it?.isPremium == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        // Profil relu à chaque compte actif : démarrage, connexion, bascule.
        viewModelScope.launch {
            observeActiveAccount().collect { userId -> if (userId != null) refreshProfile() }
        }
    }

    val accounts: StateFlow<List<Account>> =
        observeAccounts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _events = Channel<AccountsEvent>(Channel.BUFFERED)
    val events: Flow<AccountsEvent> = _events.receiveAsFlow()

    /**
     * Émet quand on passe d'un compte à un autre : bascule, ajout d'un compte, déconnexion suivie
     * d'une bascule. La lecture s'arrête et l'application revient au fil, pile vidée : les écrans
     * ouverts montraient les données de l'ancien compte. Le passage d'un visiteur à un compte, lui,
     * garde le comportement habituel (retour à l'écran qui a demandé la connexion).
     */
    val accountChanged: SharedFlow<Unit> = flow {
        var lastAccount: String? = null
        observeActiveAccount().collect { userId ->
            if (userId == null) return@collect
            if (lastAccount != null && userId != lastAccount) emit(Unit)
            lastAccount = userId
        }
    }.shareIn(viewModelScope, SharingStarted.Eagerly)

    fun select(account: Account) {
        if (account.isActive) return
        if (account.needsReconnection) {
            _events.trySend(AccountsEvent.OpenLogin(account.email))
            return
        }
        viewModelScope.launch {
            // Session expirée entre-temps : même chemin qu'un compte grisé.
            if (!switchAccount(account.userId)) _events.trySend(AccountsEvent.OpenLogin(account.email))
        }
    }

    fun remove(account: Account) {
        viewModelScope.launch { removeAccount(account) }
    }
}
