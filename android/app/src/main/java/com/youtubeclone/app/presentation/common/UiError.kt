package com.youtubeclone.app.presentation.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.DataError

/**
 * Erreur prête à afficher : un texte, avec le code HTTP éventuel comme argument.
 * Si le serveur a fourni un message ([serverMessage]), c'est lui qui est affiché.
 */
data class UiError(
    @StringRes val messageRes: Int,
    val code: Int? = null,
    val serverMessage: String? = null,
)

fun Throwable.toUiError(@StringRes notFoundRes: Int = R.string.error_unexpected): UiError =
    when (this) {
        DataError.Network -> UiError(R.string.error_network)
        DataError.NotFound -> UiError(notFoundRes)
        is DataError.Server -> UiError(R.string.error_server, code, serverMessage)
        else -> UiError(R.string.error_unexpected)
    }

@Composable
fun UiError.asText(): String = when {
    serverMessage != null -> serverMessage
    code != null -> stringResource(messageRes, code)
    else -> stringResource(messageRes)
}

/** Même texte que [asText], hors composition (par exemple pour un Toast). */
fun UiError.asText(context: Context): String = when {
    serverMessage != null -> serverMessage
    code != null -> context.getString(messageRes, code)
    else -> context.getString(messageRes)
}
