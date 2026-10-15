package com.youtubeclone.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.youtubeclone.app.presentation.onboarding.AppStartState
import com.youtubeclone.app.presentation.onboarding.AppStartViewModel
import com.youtubeclone.app.presentation.onboarding.OnboardingScreen
import com.youtubeclone.app.presentation.onboarding.StartupScreen

/**
 * Point d'entrée de l'interface : écran d'accueil au tout premier lancement, sinon directement
 * l'application. L'écran de chargement couvre la lecture des préférences, pour éviter qu'on voie
 * l'accueil une fraction de seconde avant l'écran du premier lancement.
 */
@Composable
fun AppRoot(viewModel: AppStartViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (state) {
        AppStartState.Loading -> StartupScreen()
        AppStartState.Onboarding -> OnboardingScreen()
        AppStartState.Ready -> AppNavigation()
    }
}
