package com.youtubeclone.app.presentation.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeclone.app.domain.model.Channel
import com.youtubeclone.app.domain.model.LIVE_MIN_FOLLOWERS
import com.youtubeclone.app.domain.model.Live
import com.youtubeclone.app.domain.model.PickedVideoProblem
import com.youtubeclone.app.domain.model.Video
import com.youtubeclone.app.domain.model.VideoFormat
import com.youtubeclone.app.domain.model.VideoVisibility
import com.youtubeclone.app.domain.usecase.CheckPickedVideoUseCase
import com.youtubeclone.app.domain.usecase.GetChannelPageUseCase
import com.youtubeclone.app.domain.usecase.GetOwnChannelUseCase
import com.youtubeclone.app.domain.usecase.LiveUseCases
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

/**
 * Onglet En direct : abonnés réels de la chaîne, comparés au seuil de déblocage (ADR-015). Seuil
 * atteint, [videos] sont les vidéos que le serveur accepte comme flux (publiques, non Premium) et
 * [running] le live déjà en cours de la chaîne, s'il y en a un (ADR-029).
 */
sealed interface LiveUiState {
    data object Loading : LiveUiState
    data class Error(val error: UiError) : LiveUiState
    data class Loaded(
        val followerCount: Int,
        val videos: List<Video> = emptyList(),
        val running: Live? = null,
    ) : LiveUiState
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
    private val getChannelPage: GetChannelPageUseCase,
    private val lives: LiveUseCases,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CreateUiState>(CreateUiState.Loading)
    val uiState: StateFlow<CreateUiState> = _uiState.asStateFlow()

    private val _pick = MutableStateFlow(PickUiState())
    val pick: StateFlow<PickUiState> = _pick.asStateFlow()

    private val _live = MutableStateFlow<LiveUiState>(LiveUiState.Loading)
    val live: StateFlow<LiveUiState> = _live.asStateFlow()

    private val _isStartingLive = MutableStateFlow(false)
    val isStartingLive: StateFlow<Boolean> = _isStartingLive.asStateFlow()

    // Refus du serveur au démarrage (moins de 50 abonnés, vidéo refusée, live déjà en cours...).
    private val _startLiveError = MutableStateFlow<UiError?>(null)
    val startLiveError: StateFlow<UiError?> = _startLiveError.asStateFlow()

    // Live démarré : la feuille se ferme et son écran s'ouvre.
    private val _openLive = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val openLive: SharedFlow<String> = _openLive.asSharedFlow()

    private val _openPublish = MutableSharedFlow<PickedForPublish>(extraBufferCapacity = 1)
    val openPublish: SharedFlow<PickedForPublish> = _openPublish.asSharedFlow()

    private var loadJob: Job? = null
    private var pickJob: Job? = null
    private var liveJob: Job? = null

    /** Relu à chaque ouverture : la session ou la chaîne ont pu changer depuis. */
    fun load() {
        loadJob?.cancel()
        _uiState.value = CreateUiState.Loading
        _pick.value = PickUiState()
        liveJob?.cancel()
        _live.value = LiveUiState.Loading
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
     * Onglet En direct ouvert : nombre d'abonnés de la chaîne, lu sur sa page. Chargé une fois par
     * ouverture de la feuille ; [force] le relit après une erreur.
     */
    fun loadLive(force: Boolean = false) {
        val channel = (_uiState.value as? CreateUiState.Ready)?.channel ?: return
        if (!force && (liveJob?.isActive == true || _live.value is LiveUiState.Loaded)) return
        liveJob?.cancel()
        _live.value = LiveUiState.Loading
        liveJob = viewModelScope.launch {
            _live.value = try {
                val page = getChannelPage(channel.id)
                val unlocked = page.followStatus.followerCount >= LIVE_MIN_FOLLOWERS
                LiveUiState.Loaded(
                    followerCount = page.followStatus.followerCount,
                    videos = if (unlocked) page.videos.filter { it.visibility == VideoVisibility.PUBLIC && !it.isPremium } else emptyList(),
                    // Un échec de cette lecture n'empêche pas de démarrer : le serveur refuse un second live.
                    running = if (unlocked) runCatching { lives.repository.getRunningLives(channel.id).firstOrNull() }.getOrNull() else null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LiveUiState.Error(e.toUiError())
            }
        }
    }

    /** Démarre le live avec [videoId] comme flux. Le serveur est seul juge : seuil, vidéo admissible, live déjà en cours. */
    fun startLive(title: String, videoId: String) {
        if (_isStartingLive.value || title.isBlank()) return
        _isStartingLive.value = true
        _startLiveError.value = null
        viewModelScope.launch {
            try {
                _openLive.tryEmit(lives.start(title, videoId).id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _startLiveError.value = e.toUiError()
            } finally {
                _isStartingLive.value = false
            }
        }
    }

    fun clearStartLiveError() {
        _startLiveError.value = null
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
