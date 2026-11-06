package com.youtubeclone.app.presentation.crosslive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.youtubeclone.app.data.live.SharedLiveSdk
import com.youtubeclone.app.domain.repository.LivesRepository
import com.youtubeclone.app.presentation.theme.YoutubeRedFill
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.echimine.live.core.Live
import fr.echimine.live.ui.LiveLaunchScreen
import fr.echimine.live.ui.LiveListScreen
import fr.echimine.live.ui.LiveSdkTheme
import fr.echimine.live.ui.LiveTheme
import javax.inject.Inject
import fr.echimine.live.ui.LiveScreen as SharedLiveScreen

// Lives partagés entre les applications de la classe (ADR-031) : les trois écrans du SDK, posés dans
// nos routes. La navigation reste la nôtre : chaque écran rend la main par ses callbacks. Notre live
// shopping (presentation/live, ADR-029) n'est pas concerné.

/** Configure le SDK à la première ouverture d'un écran Crosslive, y compris après une restauration. */
@HiltViewModel
class CrossliveViewModel @Inject constructor(
    val sdk: SharedLiveSdk,
    lives: LivesRepository,
) : ViewModel() {
    /** Nos produits, proposés aux Animateurs pour l'épinglage. */
    val productSource = CatalogProductSource(lives)

    init {
        sdk.ensureConfigured()
    }
}

// Thème du SDK aux couleurs de l'application : le rouge des boutons pleins (texte blanc lisible,
// contraste 4,53, ADR-030), les arrondis de nos cartes, la police par défaut de notre thème. Le
// libellé reste « Crosslive », commun à toutes les applications, qui distingue ces Lives des nôtres.
private val CrossliveTheme = LiveTheme(
    accent = YoutubeRedFill,
    cornerRadius = 12.dp,
    fontFamily = FontFamily.Default,
    label = "Crosslive",
)

@Composable
private fun CrossliveFrame(content: @Composable (CrossliveViewModel) -> Unit) {
    // Le ViewModel configure le SDK avant que l'écran ne lise LiveSdk.client.
    val viewModel: CrossliveViewModel = hiltViewModel()
    LiveSdkTheme(CrossliveTheme) {
        Box(Modifier.fillMaxSize()) { content(viewModel) }
    }
}

/** Liste des Lives en cours de toutes les applications, et champ de lien direct. */
@Composable
fun CrossliveListDestination(onOpenLive: (liveId: String) -> Unit, onLaunch: () -> Unit) {
    CrossliveFrame {
        LiveListScreen(onOpenLive = onOpenLive, onLaunch = onLaunch)
    }
}

/**
 * Lancement : aperçu caméra, vérifications, titre. Les permissions caméra et micro sont demandées
 * ici, par le SDK, après explication, et jamais au démarrage de l'application.
 */
@Composable
fun CrossliveLaunchDestination(onLaunched: (liveId: String) -> Unit, onClose: () -> Unit) {
    CrossliveFrame { viewModel ->
        LiveLaunchScreen(
            onLaunched = { live: Live ->
                viewModel.sdk.holdLaunched(live)
                onLaunched(live.id)
            },
            onClose = onClose,
        )
    }
}

/**
 * Le Live en plein écran. Pour l'Hôte qui vient de lancer, le Live déjà connecté est repris ; sinon
 * il est ouvert par son identifiant.
 */
@Composable
fun CrossliveLiveDestination(liveId: String, onClose: () -> Unit) {
    CrossliveFrame { viewModel ->
        val launched = remember(liveId) { viewModel.sdk.takeLaunched(liveId) }
        if (launched != null) {
            SharedLiveScreen(launched, onClose = onClose, productSource = viewModel.productSource)
        } else {
            SharedLiveScreen(liveId, onClose = onClose, productSource = viewModel.productSource)
        }
    }
}
