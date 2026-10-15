package com.youtubeclone.app.presentation.common

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.Flow

/** Affiche brièvement l'échec d'une action (réseau, session expirée), sans quitter l'écran. */
@Composable
fun ErrorToast(errors: Flow<UiError>) {
    val context = LocalContext.current
    LaunchedEffect(errors) {
        errors.collect { error ->
            Toast.makeText(context, error.asText(context), Toast.LENGTH_SHORT).show()
        }
    }
}
