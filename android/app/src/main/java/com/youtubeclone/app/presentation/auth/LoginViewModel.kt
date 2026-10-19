package com.youtubeclone.app.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeclone.app.domain.model.DataError
import com.youtubeclone.app.domain.usecase.LoginUseCase
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

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: UiError? = null,
    val passwordError: UiError? = null,
    val isSubmitting: Boolean = false,
    val isLoggedIn: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val login: LoginUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // Erreurs sans champ concerné (réseau, serveur indisponible), affichées en message bref.
    private val _errors = MutableSharedFlow<UiError>(extraBufferCapacity = 1)
    val errors: SharedFlow<UiError> = _errors.asSharedFlow()

    fun onEmailChange(email: String) =
        _uiState.update { it.copy(email = email, emailError = null) }

    fun onPasswordChange(password: String) =
        _uiState.update { it.copy(password = password, passwordError = null) }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting) return
        val email = state.email.trim()
        val emailError = AuthValidation.email(email)?.let(::UiError)
        val passwordError = AuthValidation.loginPassword(state.password)?.let(::UiError)
        if (emailError != null || passwordError != null) {
            _uiState.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }
        _uiState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            try {
                login(email, state.password)
                _uiState.update { it.copy(isSubmitting = false, isLoggedIn = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 401 : identifiants refusés (« Email ou mot de passe incorrect »), sous le mot de passe.
                if (e is DataError.Server && e.code == HTTP_UNAUTHORIZED) {
                    _uiState.update { it.copy(isSubmitting = false, passwordError = e.toUiError()) }
                } else {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _errors.tryEmit(e.toUiError())
                }
            }
        }
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}
