package com.youtubeclone.app.presentation.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeclone.app.domain.model.Channel
import com.youtubeclone.app.domain.model.PickedVideoProblem
import com.youtubeclone.app.domain.model.VideoFormat
import com.youtubeclone.app.domain.usecase.CheckPickedVideoUseCase
import com.youtubeclone.app.domain.usecase.GetOwnChannelUseCase
import com.youtubeclone.app.domain.usecase.ObserveSessionUseCase
import com.youtubeclone.app.presentation.common.UiError
import com.youtubeclone.app.presentation.common.toUiError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface CreateUiState {
    data object Loading : CreateUiState
    data object LoggedOut : CreateUiState
    /** Connecté sans chaîne : la feuille invite à en créer une (ADR-015). */
    data object NoChannel : CreateUiState
    data class Error(val error: UiError) : CreateUiState
    data class Ready(val channel: Channel) : CreateUiState
}

/** Vidéo choisie et acceptée : l'écran de publication s'ouvre avec elle. */
data class PickedForPublish(val uri: String, val format: VideoFormat)

/** Vérification de la vidéo choisie : en cours, ou refusée avec sa raison. */
data class PickUiState(
    val isChecking: Boolean = false,
    val problem: PickedVideoProblem? = null,
)

/** Feuille de création du bouton + : seul le propriétaire d'une chaîne peut publier (ADR-015). */
@HiltViewModel
class CreateViewModel @Inject constructor(
    private val observeSession: ObserveSessionUseCase,
    private val getOwnChannel: GetOwnChannelUseCase,
    private val checkPickedVideo: CheckPickedVideoUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CreateUiState>(CreateUiState.Loading)
    val uiState: StateFlow<CreateUiState> = _uiState.asStateFlow()

    private val _pick = MutableStateFlow(PickUiState())
    val pick: StateFlow<PickUiState> = _pick.asStateFlow()

    private val _openPublish = MutableSharedFlow<PickedForPublish>(extraBufferCapacity = 1)
    val openPublish: SharedFlow<PickedForPublish> = _openPublish.asSharedFlow()

    private var loadJob: Job? = null
    private var pickJob: Job? = null

    /** Relu à chaque ouverture : la session ou la chaîne ont pu changer depuis. */
    fun load() {
        loadJob?.cancel()
        _uiState.value = CreateUiState.Loading
        _pick.value = PickUiState()
        loadJob = viewModelScope.launch {
            _uiState.value = try {
                when {
                    !observeSession().first() -> CreateUiState.LoggedOut
                    else -> getOwnChannel()?.let(CreateUiState::Ready) ?: CreateUiState.NoChannel
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CreateUiState.Error(e.toUiError())
            }
        }
    }

    /**
     * Vidéo choisie dans le sélecteur ([uri] null : sélecteur fermé sans choix). Un fichier qui
     * ne convient pas au format est refusé ici, avant tout envoi ; le serveur revérifie.
     */
    fun onVideoPicked(uri: String?, format: VideoFormat) {
        if (uri == null) return
        pickJob?.cancel()
        _pick.value = PickUiState(isChecking = true)
        pickJob = viewModelScope.launch {
            val problem = checkPickedVideo(uri, format)
            _pick.value = PickUiState(problem = problem)
            if (problem == null) _openPublish.tryEmit(PickedForPublish(uri, format))
        }
    }

    /** Changement d'onglet : le refus affiché concernait l'onglet précédent. */
    fun clearPickProblem() {
        _pick.value = _pick.value.copy(problem = null)
    }
}
