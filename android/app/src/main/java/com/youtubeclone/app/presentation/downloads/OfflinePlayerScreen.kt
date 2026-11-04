package com.youtubeclone.app.presentation.downloads

import android.view.View
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.youtubeclone.app.R
import com.youtubeclone.app.presentation.common.BackTopBar
import com.youtubeclone.app.presentation.common.LoadingState

/** Lecteur hors ligne d'une vidéo téléchargée (ADR-019) : la vidéo, son titre et sa chaîne. */
@OptIn(UnstableApi::class)
@Composable
fun OfflinePlayerScreen(onBack: () -> Unit, viewModel: OfflinePlayerViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = stringResource(R.string.downloads_title), onBack = onBack)
        when (val current = state) {
            OfflinePlayerUiState.Loading -> LoadingState(Modifier.weight(1f))
            is OfflinePlayerUiState.Refused -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(
                        if (current.needsRevalidation) R.string.downloads_refused_revalidation else R.string.downloads_refused,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
            is OfflinePlayerUiState.Playing -> {
                AndroidView(
                    factory = { context ->
                        PlayerView(context).apply {
                            player = viewModel.player
                            setShowSubtitleButton(true)
                            // Pas de menu de Media3 : la qualité est celle du fichier téléchargé.
                            findViewById<View>(androidx.media3.ui.R.id.exo_settings)?.visibility = View.GONE
                        }
                    },
                    onRelease = { it.player = null },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black),
                )
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(current.video.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        current.video.channelName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
