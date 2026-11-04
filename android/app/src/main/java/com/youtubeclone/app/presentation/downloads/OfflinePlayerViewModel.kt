package com.youtubeclone.app.presentation.downloads

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.navigation.toRoute
import com.youtubeclone.app.domain.model.DownloadedVideo
import com.youtubeclone.app.domain.usecase.GetDownloadUseCase
import com.youtubeclone.app.presentation.navigation.OfflinePlayerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface OfflinePlayerUiState {
    data object Loading : OfflinePlayerUiState
    /** Lecture refusée : vidéo absente, Premium terminé ou revalidation de plus de 30 jours. */
    data class Refused(val needsRevalidation: Boolean) : OfflinePlayerUiState
    data class Playing(val video: DownloadedVideo) : OfflinePlayerUiState
}

/**
 * Lecture hors ligne (ADR-019) : depuis le cache privé des téléchargements, sans aucun appel à
 * l'API ni connexion temps réel. Ni vue comptée, ni historique (décision 13).
 */
@OptIn(UnstableApi::class)
@HiltViewModel
class OfflinePlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext context: Context,
    private val getDownload: GetDownloadUseCase,
    private val downloadManager: DownloadManager,
    cache: SimpleCache,
) : ViewModel() {

    private val videoId = savedStateHandle.toRoute<OfflinePlayerRoute>().videoId

    // Lecture seule depuis le cache : sans source réseau, rien n'est jamais demandé au serveur.
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(null)),
        )
        .build()

    private val _uiState = MutableStateFlow<OfflinePlayerUiState>(OfflinePlayerUiState.Loading)
    val uiState: StateFlow<OfflinePlayerUiState> = _uiState.asStateFlow()

    // Miniature par-dessus le lecteur tant que la première image n'est pas rendue.
    private val _showThumbnail = MutableStateFlow(true)
    val showThumbnail: StateFlow<Boolean> = _showThumbnail.asStateFlow()

    private val firstFrameListener = object : Player.Listener {
        override fun onRenderedFirstFrame() {
            _showThumbnail.value = false
        }
    }

    init {
        player.addListener(firstFrameListener)
        viewModelScope.launch {
            val video = getDownload(videoId)
            val request = withContext(Dispatchers.IO) { downloadManager.downloadIndex.getDownload(videoId)?.request }
            if (video == null || !video.playable || request == null) {
                _uiState.value = OfflinePlayerUiState.Refused(needsRevalidation = video?.needsRevalidation == true)
                return@launch
            }
            // Sous-titres enregistrés avec la vidéo : désactivés au départ, comme en ligne.
            val item = request.toMediaItem().buildUpon()
                .setSubtitleConfigurations(
                    listOfNotNull(
                        video.subtitlePath?.let {
                            MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(File(it)))
                                .setMimeType(MimeTypes.TEXT_VTT)
                                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                .build()
                        },
                    ),
                )
                .build()
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            player.setMediaItem(item)
            player.prepare()
            player.playWhenReady = true
            _uiState.value = OfflinePlayerUiState.Playing(video)
        }
    }

    /**
     * Écran masqué : la lecture continue en arrière-plan, comme avant, mais sans image. Le décodeur
     * vidéo est ainsi libéré : gardé pendant que la surface d'affichage est détruite puis recréée,
     * il affiche une image corrompue au retour (constaté sur l'émulateur).
     */
    fun onScreenStopped() {
        setVideoTrackDisabled(true)
        _showThumbnail.value = true
    }

    /** Retour : l'image revient ; la miniature reste affichée jusqu'à la première image. */
    fun onScreenStarted() = setVideoTrackDisabled(false)

    private fun setVideoTrackDisabled(disabled: Boolean) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, disabled)
            .build()
    }

    override fun onCleared() {
        player.removeListener(firstFrameListener)
        player.release()
    }
}
