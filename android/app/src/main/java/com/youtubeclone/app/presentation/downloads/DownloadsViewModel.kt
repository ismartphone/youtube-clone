package com.youtubeclone.app.presentation.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeclone.app.domain.model.DownloadedVideo
import com.youtubeclone.app.domain.usecase.ObserveDownloadsUseCase
import com.youtubeclone.app.domain.usecase.RemoveDownloadUseCase
import com.youtubeclone.app.domain.usecase.RevalidateDownloadsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Écran « Téléchargements » (ADR-019) : fonctionne sans réseau, à partir des données locales. */
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    observeDownloads: ObserveDownloadsUseCase,
    private val removeDownload: RemoveDownloadUseCase,
    revalidate: RevalidateDownloadsUseCase,
) : ViewModel() {

    /** null tant que la liste locale n'est pas lue. */
    val downloads: StateFlow<List<DownloadedVideo>?> = observeDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Avec du réseau, l'ouverture de l'écran revalide aussi les vidéos ; sans réseau, rien ne change.
        viewModelScope.launch { runCatching { revalidate() } }
    }

    fun remove(videoId: String) {
        viewModelScope.launch { removeDownload(videoId) }
    }
}
