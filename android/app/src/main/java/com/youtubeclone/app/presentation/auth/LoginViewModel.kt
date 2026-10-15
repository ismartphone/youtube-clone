package com.youtubeclone.app.presentation.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeclone.app.domain.usecase.LoginUseCase
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

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    @StringRes val emailError: Int? = null,
    @StringRes val passwordError: Int? = null,
    val submitError: UiError? = null,
    val isSubmitting: Boolean = false,
    val isLoggedIn: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val login: LoginUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) =
        _uiState.update { it.copy(email = email, emailError = null, submitError = null) }

    fun onPasswordChange(password: String) =
        _uiState.update { it.copy(password = password, passwordError = null, submitError = null) }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting) return
        val email = state.email.trim()
        val emailError = AuthValidation.email(email)
        val passwordError = AuthValidation.loginPassword(state.password)
        if (emailError != null || passwordError != null) {
            _uiState.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }
        _uiState.update { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            try {
                login(email, state.password)
                _uiState.update { it.copy(isSubmitting = false, isLoggedIn = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSubmitting = false, submitError = e.toUiError()) }
            }
        }
    }
}
