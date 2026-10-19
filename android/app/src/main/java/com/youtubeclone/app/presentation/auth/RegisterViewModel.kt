package com.youtubeclone.app.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeclone.app.domain.model.DataError
import com.youtubeclone.app.domain.usecase.RegisterUseCase
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

data class RegisterUiState(
    val displayName: String = "",
    val email: String = "",
    val password: String = "",
    val displayNameError: UiError? = null,
    val emailError: UiError? = null,
    val passwordError: UiError? = null,
    val isSubmitting: Boolean = false,
    val isRegistered: Boolean = false,
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val register: RegisterUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    // Erreurs sans champ concerné (réseau, serveur indisponible), affichées en message bref.
    private val _errors = MutableSharedFlow<UiError>(extraBufferCapacity = 1)
    val errors: SharedFlow<UiError> = _errors.asSharedFlow()

    fun onDisplayNameChange(displayName: String) =
        _uiState.update { it.copy(displayName = displayName, displayNameError = null) }

    fun onEmailChange(email: String) =
        _uiState.update { it.copy(email = email, emailError = null) }

    fun onPasswordChange(password: String) =
        _uiState.update { it.copy(password = password, passwordError = null) }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting) return
        val displayName = state.displayName.trim()
        val email = state.email.trim()
        val displayNameError = AuthValidation.displayName(displayName)?.let(::UiError)
        val emailError = AuthValidation.email(email)?.let(::UiError)
        val passwordError = AuthValidation.newPassword(state.password)?.let(::UiError)
        if (displayNameError != null || emailError != null || passwordError != null) {
            _uiState.update {
                it.copy(displayNameError = displayNameError, emailError = emailError, passwordError = passwordError)
            }
            return
        }
        _uiState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            try {
                register(email, state.password, displayName)
                _uiState.update { it.copy(isSubmitting = false, isRegistered = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 409 : adresse déjà utilisée, sous le champ email.
                if (e is DataError.Server && e.code == HTTP_CONFLICT) {
                    _uiState.update { it.copy(isSubmitting = false, emailError = e.toUiError()) }
                } else {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _errors.tryEmit(e.toUiError())
                }
            }
        }
    }

    private companion object {
        const val HTTP_CONFLICT = 409
    }
}
