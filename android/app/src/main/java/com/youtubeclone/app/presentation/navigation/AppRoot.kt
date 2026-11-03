package com.youtubeclone.app.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.youtubeclone.app.presentation.onboarding.AppStartState
import com.youtubeclone.app.presentation.onboarding.AppStartViewModel
import com.youtubeclone.app.presentation.onboarding.OnboardingScreen
import com.youtubeclone.app.presentation.onboarding.OpeningAnimation

/**
 * Point d'entrée de l'interface : écran d'accueil au tout premier lancement, sinon directement
 * l'application. L'animation d'ouverture recouvre l'écran choisi, qui charge dessous en même temps.
 */
@Composable
fun AppRoot(viewModel: AppStartViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isAppReady by viewModel.isAppReady.collectAsStateWithLifecycle()
    // Jouée une fois par lancement : ni une rotation ni un retour de l'arrière-plan ne la rejouent.
    var openingDone by rememberSaveable { mutableStateOf(false) }
    // « Se connecter » sur l'écran d'accueil : la connexion s'ouvre au-dessus du fil, une seule fois.
    var loginRequested by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        when (state) {
            AppStartState.Loading -> Unit // préférences en cours de lecture, sous l'animation
            AppStartState.Onboarding -> {
                TrackScreen("Onboarding")
                OnboardingScreen(onLoginClick = { loginRequested = true })
            }
            AppStartState.Ready -> AppNavigation(
                openLoginAtStart = loginRequested,
                onLoginOpened = { loginRequested = false },
            )
        }
        if (!openingDone) {
            OpeningAnimation(isAppReady = isAppReady, onFinished = { openingDone = true })
        }
    }
}
