package com.youtubeclone.app.presentation.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
fun RegisterScreen(
    onRegistered: () -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isRegistered) {
        if (state.isRegistered) onRegistered()
    }

    AuthForm(title = R.string.register_title, modifier = modifier) {
        AuthTextField(
            value = state.displayName,
            onValueChange = viewModel::onDisplayNameChange,
            label = R.string.auth_display_name,
            error = state.displayNameError,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next,
        )
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
            supportingText = stringResource(R.string.auth_password_hint, AuthValidation.PASSWORD_MIN_LENGTH),
            isPassword = true,
            onImeAction = viewModel::submit,
        )
        SubmitError(state.submitError)
        SubmitButton(text = R.string.register_submit, isSubmitting = state.isSubmitting, onClick = viewModel::submit)
        TextButton(onClick = onLoginClick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.register_to_login))
        }
    }
}
