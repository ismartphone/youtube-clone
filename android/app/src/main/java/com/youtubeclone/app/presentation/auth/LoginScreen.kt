package com.youtubeclone.app.presentation.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.youtubeclone.app.R

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onRegisterClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) onLoggedIn()
    }

    AuthForm(title = R.string.login_title, modifier = modifier) {
        AuthTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = R.string.auth_email,
            error = state.emailError,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
        )
        AuthTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = R.string.auth_password,
            error = state.passwordError,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            isPassword = true,
            onImeAction = viewModel::submit,
        )
        SubmitError(state.submitError)
        SubmitButton(text = R.string.login_submit, isSubmitting = state.isSubmitting, onClick = viewModel::submit)
        TextButton(onClick = onRegisterClick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.login_to_register))
        }
    }
}
