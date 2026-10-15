package com.youtubeclone.app.presentation.common

import androidx.annotation.StringRes
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.DataError

/** Erreur prête à afficher : un texte, avec le code HTTP éventuel comme argument. */
data class UiError(@StringRes val messageRes: Int, val code: Int? = null)

fun Throwable.toUiError(@StringRes notFoundRes: Int = R.string.error_unexpected): UiError =
    when (this) {
        DataError.Network -> UiError(R.string.error_network)
        DataError.NotFound -> UiError(notFoundRes)
        is DataError.Server -> UiError(R.string.error_server, code)
        else -> UiError(R.string.error_unexpected)
    }
