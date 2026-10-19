package com.youtubeclone.app.presentation.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onRegisterClick: () -> Unit,
    onContinueAsVisitor: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ErrorToast(viewModel.errors)

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) onLoggedIn()
    }

    LoginContent(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::submit,
        onRegisterClick = onRegisterClick,
        onContinueAsVisitor = onContinueAsVisitor,
        modifier = modifier,
    )
}

@Composable
private fun LoginContent(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onRegisterClick: () -> Unit,
    onContinueAsVisitor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthLayout(
        title = R.string.login_title,
        modifier = modifier,
        footer = {
            AuthFooterLinks(
                switchLabel = R.string.login_to_register,
                onSwitchClick = onRegisterClick,
                onContinueAsVisitor = onContinueAsVisitor,
            )
        },
    ) {
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
            isPassword = true,
            onImeAction = onSubmit,
        )
        SubmitButton(text = R.string.login_submit, isSubmitting = state.isSubmitting, onClick = onSubmit)
    }
}

@Preview(name = "Connexion, mot de passe refusé", showBackground = true, backgroundColor = 0xFF0F0F0F, heightDp = 760)
@Composable
private fun LoginContentPreview() {
    YoutubeCloneTheme {
        LoginContent(
            state = LoginUiState(
                email = "camille@example.com",
                password = "motdepasse",
                passwordError = UiError(R.string.error_unexpected, serverMessage = "Email ou mot de passe incorrect"),
            ),
            onEmailChange = {},
            onPasswordChange = {},
            onSubmit = {},
            onRegisterClick = {},
            onContinueAsVisitor = {},
        )
    }
}

@Preview(name = "Connexion en cours", showBackground = true, backgroundColor = 0xFF0F0F0F, heightDp = 760)
@Composable
private fun LoginSubmittingPreview() {
    YoutubeCloneTheme {
        LoginContent(
            state = LoginUiState(email = "camille@example.com", password = "motdepasse", isSubmitting = true),
            onEmailChange = {},
            onPasswordChange = {},
            onSubmit = {},
            onRegisterClick = {},
            onContinueAsVisitor = {},
        )
    }
}
