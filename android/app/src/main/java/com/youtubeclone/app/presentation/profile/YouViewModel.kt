package com.youtubeclone.app.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeclone.app.domain.model.User
import com.youtubeclone.app.domain.usecase.GetCurrentUserUseCase
import com.youtubeclone.app.domain.usecase.LogoutUseCase
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

sealed interface YouUiState {
    data object Loading : YouUiState
    data object LoggedOut : YouUiState
    data class Error(val error: UiError) : YouUiState
    data class LoggedIn(val user: User) : YouUiState
}

@HiltViewModel
class YouViewModel @Inject constructor(
    observeSession: ObserveSessionUseCase,
    private val getCurrentUser: GetCurrentUserUseCase,
    private val logout: LogoutUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<YouUiState>(YouUiState.Loading)
    val uiState: StateFlow<YouUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        // Suit la session : connexion depuis un autre écran, déconnexion, ou expiration du refresh token.
        viewModelScope.launch {
            observeSession().collect { isLoggedIn ->
                if (isLoggedIn) load() else showLoggedOut()
            }
        }
    }

    fun load() {
        loadJob?.cancel()
        _uiState.value = YouUiState.Loading
        loadJob = viewModelScope.launch {
            _uiState.value = try {
                YouUiState.LoggedIn(getCurrentUser())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                YouUiState.Error(e.toUiError())
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
