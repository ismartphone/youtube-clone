package com.youtubeclone.app.presentation.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeclone.app.domain.model.Video
import com.youtubeclone.app.domain.usecase.GetSubscriptionFeedUseCase
import com.youtubeclone.app.domain.usecase.ObserveFollowChangesUseCase
import com.youtubeclone.app.domain.usecase.ObserveSessionUseCase
import com.youtubeclone.app.presentation.common.UiError
import com.youtubeclone.app.presentation.common.toUiError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SubscriptionsUiState {
    data object Loading : SubscriptionsUiState
    data object LoggedOut : SubscriptionsUiState
    data object Empty : SubscriptionsUiState
    data class Error(val error: UiError) : SubscriptionsUiState
    data class Success(val videos: List<Video>) : SubscriptionsUiState
}

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    observeSession: ObserveSessionUseCase,
    observeFollowChanges: ObserveFollowChangesUseCase,
    private val getSubscriptionFeed: GetSubscriptionFeedUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SubscriptionsUiState>(SubscriptionsUiState.Loading)
    val uiState: StateFlow<SubscriptionsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            observeSession().collect { loggedIn ->
                if (loggedIn) load() else showLoggedOut()
            }
        }
        // Un abonnement pris ailleurs (page de chaîne, lecteur) change le contenu du fil.
        viewModelScope.launch {
            observeFollowChanges().collect {
                if (_uiState.value != SubscriptionsUiState.LoggedOut) load()
            }
        }
    }

    fun load() {
        loadJob?.cancel()
        if (_uiState.value !is SubscriptionsUiState.Success) _uiState.value = SubscriptionsUiState.Loading
        loadJob = viewModelScope.launch {
            _uiState.value = try {
                // Première page seulement, comme le fil d'accueil.
                val page = getSubscriptionFeed()
                if (page.items.isEmpty()) SubscriptionsUiState.Empty else SubscriptionsUiState.Success(page.items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SubscriptionsUiState.Error(e.toUiError())
            }
        }
    }

    private fun showLoggedOut() {
        loadJob?.cancel()
        _uiState.value = SubscriptionsUiState.LoggedOut
    }
}
