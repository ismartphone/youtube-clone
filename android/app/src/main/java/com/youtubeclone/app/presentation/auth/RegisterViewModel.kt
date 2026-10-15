package com.youtubeclone.app.presentation.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeclone.app.domain.usecase.RegisterUseCase
import com.youtubeclone.app.presentation.common.UiError
import com.youtubeclone.app.presentation.common.toUiError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val displayName: String = "",
    val email: String = "",
    val password: String = "",
    @StringRes val displayNameError: Int? = null,
    @StringRes val emailError: Int? = null,
    @StringRes val passwordError: Int? = null,
    val submitError: UiError? = null,
    val isSubmitting: Boolean = false,
    val isRegistered: Boolean = false,
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val register: RegisterUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onDisplayNameChange(displayName: String) =
        _uiState.update { it.copy(displayName = displayName, displayNameError = null, submitError = null) }

    fun onEmailChange(email: String) =
        _uiState.update { it.copy(email = email, emailError = null, submitError = null) }

    fun onPasswordChange(password: String) =
        _uiState.update { it.copy(password = password, passwordError = null, submitError = null) }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting) return
        val displayName = state.displayName.trim()
        val email = state.email.trim()
        val displayNameError = AuthValidation.displayName(displayName)
        val emailError = AuthValidation.email(email)
        val passwordError = AuthValidation.newPassword(state.password)
        if (displayNameError != null || emailError != null || passwordError != null) {
            _uiState.update {
                it.copy(displayNameError = displayNameError, emailError = emailError, passwordError = passwordError)
            }
            return
        }
        _uiState.update { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            try {
                register(email, state.password, displayName)
                _uiState.update { it.copy(isSubmitting = false, isRegistered = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSubmitting = false, submitError = e.toUiError()) }
            }
        }
    }
}
