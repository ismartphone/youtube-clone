package com.youtubeclone.app.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.youtubeclone.app.presentation.account.AccountsViewModel
import com.youtubeclone.app.presentation.common.LocalIsPremiumViewer
import com.youtubeclone.app.presentation.common.LocalNowPlayingActive
import com.youtubeclone.app.presentation.onboarding.AppStartState
import com.youtubeclone.app.presentation.onboarding.AppStartViewModel
import com.youtubeclone.app.presentation.onboarding.OnboardingScreen
import com.youtubeclone.app.presentation.onboarding.OpeningAnimation
import com.youtubeclone.app.presentation.watch.WatchViewModel

/**
 * Point d'entrée de l'interface : parcours d'accueil au tout premier lancement (ADR-028), sinon directement
 * l'application. L'animation d'ouverture recouvre l'écran choisi, qui charge dessous en même temps.
 * [notifiedVideoId] : vidéo à ouvrir après un appui sur une notification (ADR-020) ;
 * [notifiedConversationId] : conversation à ouvrir après un appui sur une notification de message (ADR-025).
 */
@Composable
fun AppRoot(
    notifiedVideoId: String? = null,
    onNotifiedVideoOpened: () -> Unit = {},
    notifiedConversationId: String? = null,
    onNotifiedConversationOpened: () -> Unit = {},
    viewModel: AppStartViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isAppReady by viewModel.isAppReady.collectAsStateWithLifecycle()
    // Jouée une fois par lancement : ni une rotation ni un retour de l'arrière-plan ne la rejouent.
    var openingDone by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        when (state) {
            AppStartState.Loading -> Unit // préférences en cours de lecture, sous l'animation
            AppStartState.Onboarding -> {
                TrackScreen("Onboarding")
                OnboardingScreen()
            }
            is AppStartState.Ready -> {
                // Statut Premium du compte actif, pour toutes les miniatures (ADR-026). Même
                // instance que celle de la navigation : le ViewModel est lié à l'activité.
                val isPremium by hiltViewModel<AccountsViewModel>().isPremium.collectAsStateWithLifecycle()
                // Un lecteur est ouvert (ADR-024) : le bandeau sponsorisé du fil se suspend.
                val nowPlayingId by hiltViewModel<WatchViewModel>().currentVideoId.collectAsStateWithLifecycle()
                CompositionLocalProvider(
                    LocalIsPremiumViewer provides isPremium,
                    LocalNowPlayingActive provides (nowPlayingId != null),
                ) {
                    AppNavigation(
                        startOnShorts = (state as AppStartState.Ready).startOnShorts,
                        notifiedVideoId = notifiedVideoId,
                        onNotifiedVideoOpened = onNotifiedVideoOpened,
                        notifiedConversationId = notifiedConversationId,
                        onNotifiedConversationOpened = onNotifiedConversationOpened,
                    )
                }
            }
        }
        if (!openingDone) {
            OpeningAnimation(isAppReady = isAppReady, onFinished = { openingDone = true })
        }
    }
}
