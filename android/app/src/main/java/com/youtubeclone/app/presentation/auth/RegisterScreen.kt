package com.youtubeclone.app.presentation.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.youtubeclone.app.R
import com.youtubeclone.app.presentation.common.ErrorToast
import com.youtubeclone.app.presentation.common.UiError
import com.youtubeclone.app.presentation.theme.YoutubeCloneTheme

@Composable
fun RegisterScreen(
    onRegistered: () -> Unit,
    onLoginClick: () -> Unit,
    onContinueAsVisitor: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ErrorToast(viewModel.errors)

    LaunchedEffect(state.isRegistered) {
        if (state.isRegistered) onRegistered()
    }

    RegisterContent(
        state = state,
        onDisplayNameChange = viewModel::onDisplayNameChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::submit,
        onLoginClick = onLoginClick,
        onContinueAsVisitor = onContinueAsVisitor,
        modifier = modifier,
    )
}

@Composable
private fun RegisterContent(
    state: RegisterUiState,
    onDisplayNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onLoginClick: () -> Unit,
    onContinueAsVisitor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthLayout(
        title = R.string.register_title,
        modifier = modifier,
        footer = {
            AuthFooterLinks(
                switchLabel = R.string.register_to_login,
                onSwitchClick = onLoginClick,
                onContinueAsVisitor = onContinueAsVisitor,
            )
        },
    ) {
        AuthTextField(
            value = state.displayName,
            onValueChange = onDisplayNameChange,
            label = R.string.auth_display_name,
            error = state.displayNameError,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next,
        )
        AuthTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = R.string.auth_email,
            error = state.emailError,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
        )
        AuthTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = R.string.auth_password,
            error = state.passwordError,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            supportingText = stringResource(R.string.auth_password_hint, AuthValidation.PASSWORD_MIN_LENGTH),
            isPassword = true,
            onImeAction = onSubmit,
        )
        SubmitButton(text = R.string.register_submit, isSubmitting = state.isSubmitting, onClick = onSubmit)
    }
}

@Preview(name = "Inscription, email déjà utilisé", showBackground = true, backgroundColor = 0xFF0F0F0F, heightDp = 800)
@Composable
private fun RegisterContentPreview() {
    YoutubeCloneTheme {
        RegisterContent(
            state = RegisterUiState(
                displayName = "Camille",
                email = "camille@example.com",
                emailError = UiError(R.string.error_unexpected, serverMessage = "Cette adresse email est déjà utilisée"),
            ),
            onDisplayNameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onSubmit = {},
            onLoginClick = {},
            onContinueAsVisitor = {},
        )
    }
}
