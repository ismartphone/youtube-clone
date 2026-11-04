package com.youtubeclone.app.presentation.ads

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.youtubeclone.app.domain.analytics.Analytics
import com.youtubeclone.app.domain.model.VideoAd
import com.youtubeclone.app.presentation.common.BackTopBar
import com.youtubeclone.app.presentation.navigation.AdPlayerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Publicité du bandeau sponsorisé lue en plein lecteur (ADR-021), avec le même « Ignorer » que
 * l'annonce avant une vidéo. Ignorée ou terminée, elle rend la main au fil.
 */
@HiltViewModel
class AdPlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext context: Context,
    analytics: Analytics,
) : ViewModel() {
    val ads = AdPlaybackController(context, viewModelScope, analytics)

    private val _finished = Channel<Unit>(Channel.CONFLATED)
    val finished = _finished.receiveAsFlow()

    init {
        val route = savedStateHandle.toRoute<AdPlayerRoute>()
        ads.play(
            VideoAd(
                id = route.adId,
                title = route.title,
                streamUrl = route.streamUrl,
                isHls = route.isHls,
                thumbnailUrl = null,
                durationSeconds = route.durationSeconds,
            ),
            onFinished = { _finished.trySend(Unit) },
        )
    }

    override fun onCleared() = ads.release()
}

@Composable
fun AdPlayerScreen(onClose: () -> Unit, viewModel: AdPlayerViewModel = hiltViewModel()) {
    val state by viewModel.ads.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.finished.collect { onClose() } }
    // Écran masqué : l'annonce attend, elle ne se joue jamais en arrière-plan.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.ads.pause() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.ads.resume() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        state?.let {
            AdPlaybackOverlay(
                state = it,
                player = viewModel.ads.player,
                onSkip = viewModel.ads::skip,
                onRemoveAds = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .align(Alignment.Center),
            )
        }
        BackTopBar(title = "", onBack = onClose, contentColor = Color.White, modifier = Modifier.align(Alignment.TopStart))
    }
}
