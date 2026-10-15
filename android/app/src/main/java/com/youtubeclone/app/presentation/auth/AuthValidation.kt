package com.youtubeclone.app.presentation.auth

import android.util.Patterns
import androidx.annotation.StringRes
import com.youtubeclone.app.R

// Mêmes règles que la validation du backend (auth.bodies.ts), pour signaler l'erreur avant l'envoi.
// Chaque fonction renvoie le texte de l'erreur, ou null si le champ est valide.
internal object AuthValidation {
    private const val EMAIL_MAX_LENGTH = 254
    const val PASSWORD_MIN_LENGTH = 8
    private const val PASSWORD_MAX_LENGTH = 128
    private const val DISPLAY_NAME_MAX_LENGTH = 50

    @StringRes
    fun email(email: String): Int? = when {
        email.isBlank() -> R.string.auth_error_email_required
        email.length > EMAIL_MAX_LENGTH || !Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
            R.string.auth_error_email_invalid
        else -> null
    }

    /** À la connexion, seule la présence du mot de passe est vérifiée. */
    @StringRes
    fun loginPassword(password: String): Int? = when {
        password.isEmpty() -> R.string.auth_error_password_required
        password.length > PASSWORD_MAX_LENGTH -> R.string.auth_error_password_too_long
        else -> null
    }

    @StringRes
    fun newPassword(password: String): Int? = when {
        password.isEmpty() -> R.string.auth_error_password_required
        password.length < PASSWORD_MIN_LENGTH -> R.string.auth_error_password_too_short
        password.length > PASSWORD_MAX_LENGTH -> R.string.auth_error_password_too_long
        else -> null
    }

    @StringRes
    fun displayName(displayName: String): Int? = when {
        displayName.isBlank() -> R.string.auth_error_display_name_required
        displayName.length > DISPLAY_NAME_MAX_LENGTH -> R.string.auth_error_display_name_too_long
        else -> null
    }
}
