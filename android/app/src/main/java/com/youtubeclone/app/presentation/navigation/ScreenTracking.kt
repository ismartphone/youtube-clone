package com.youtubeclone.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import com.youtubeclone.app.domain.analytics.Analytics
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Mesure « écran affiché » (ADR-006). */
@HiltViewModel
class ScreenTrackingViewModel @Inject constructor(
    private val analytics: Analytics,
) : ViewModel() {
    fun onScreen(name: String) = analytics.screen(name)
}

/**
 * Envoie chaque destination affichée par [navController]. Le nom vient de la route typée
 * (« WatchRoute/{videoId} » donne « Watch ») : jamais l'identifiant d'une vidéo ou d'une chaîne.
 */
@Composable
fun TrackScreens(navController: NavController, viewModel: ScreenTrackingViewModel = hiltViewModel()) {
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            destination.route?.let { viewModel.onScreen(screenName(it)) }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
}

/** Écran affiché hors navigation, comme l'écran d'accueil du premier lancement. */
@Composable
fun TrackScreen(name: String, viewModel: ScreenTrackingViewModel = hiltViewModel()) {
    LaunchedEffect(name) { viewModel.onScreen(name) }
}

private fun screenName(route: String): String =
    route.substringBefore('/').substringBefore('?').substringAfterLast('.').removeSuffix("Route")
