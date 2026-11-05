package com.youtubeclone.app.presentation.live

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.toRoute
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.LIVE_CHAT_MAX_LENGTH
import com.youtubeclone.app.domain.model.Live
import com.youtubeclone.app.domain.model.LiveChatMessage
import com.youtubeclone.app.domain.model.LiveRoomEvent
import com.youtubeclone.app.domain.usecase.GetOwnChannelUseCase
import com.youtubeclone.app.domain.usecase.LiveUseCases
import com.youtubeclone.app.presentation.common.UiError
import com.youtubeclone.app.presentation.common.toUiError
import com.youtubeclone.app.presentation.navigation.LiveRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface LiveUiState {
    data object Loading : LiveUiState
    data class Error(val error: UiError) : LiveUiState
    data class Ready(
        val live: Live,
        /** Compte connecté ; null pour un visiteur, qui lit le chat sans pouvoir écrire. */
        val myId: String?,
        /** Propriétaire de la chaîne du live : seul à pouvoir le terminer. */
        val isBroadcaster: Boolean,
        /** Du plus récent au plus ancien : la liste s'affiche à l'envers, le plus récent en bas. */
        val messages: List<LiveChatMessage>,
        val viewerCount: Int,
        val isEnded: Boolean,
    ) : LiveUiState
}

/**
 * Écran d'un live (ADR-029) : la vidéo du flux en boucle, le chat et le compteur de spectateurs,
 * reçus en direct tant que l'écran est ouvert. Il a son propre lecteur, libéré en le quittant : le
 * live n'a pas de mini-lecteur (D15), la salle se ferme avec l'écran.
 */
@HiltViewModel
class LiveViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext context: Context,
    private val lives: LiveUseCases,
    private val getOwnChannel: GetOwnChannelUseCase,
) : ViewModel() {

    val liveId = savedStateHandle.toRoute<LiveRoute>().liveId

    private val _uiState = MutableStateFlow<LiveUiState>(LiveUiState.Loading)
    val uiState: StateFlow<LiveUiState> = _uiState.asStateFlow()

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _errors = MutableSharedFlow<UiError>(extraBufferCapacity = 1)
    val errors: SharedFlow<UiError> = _errors.asSharedFlow()

    /** Lecteur du flux, propre à cet écran : le même fichier en boucle, comme un direct. */
    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        repeatMode = Player.REPEAT_MODE_ALL
    }

    private var streamLoaded = false
    private var shown = false
    private var hasConnectedOnce = false

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                val live = lives.repository.getLive(liveId)
                val messages = if (live.isLive) lives.repository.getMessages(liveId) else emptyList()
                val myId = lives.currentUserId()
                val isBroadcaster = myId != null && runCatching { getOwnChannel()?.id }.getOrNull() == live.channel.id
                _uiState.value = LiveUiState.Ready(
                    live = live,
                    myId = myId,
                    isBroadcaster = isBroadcaster,
                    messages = messages,
                    viewerCount = live.viewerCount,
                    isEnded = !live.isLive,
                )
                if (live.isLive) startPlayback(live) else player.stop()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Un rechargement qui échoue garde l'écran affiché.
                if (_uiState.value !is LiveUiState.Ready) {
                    _uiState.value = LiveUiState.Error(e.toUiError(notFoundRes = R.string.live_not_found))
                }
            }
        }
    }

    // Le fichier est lu en boucle depuis la position commune à tous les spectateurs (ADR-029, D3).
    private fun startPlayback(live: Live) {
        if (streamLoaded) return
        val stream = live.stream ?: return
        val hls = stream.hlsUrl
        val item = MediaItem.Builder()
            .setUri(hls ?: stream.videoUrl)
            .setMimeType(if (hls != null) MimeTypes.APPLICATION_M3U8 else null)
            .build()
        player.setMediaItem(item, live.playbackPositionMs(Instant.now()))
        player.prepare()
        player.playWhenReady = shown
        streamLoaded = true
    }

    /** Écran visible : la lecture reprend à la position du direct. En arrière-plan, elle se met en pause. */
    fun onShown(visible: Boolean) {
        shown = visible
        val ready = _uiState.value as? LiveUiState.Ready
        if (!streamLoaded || ready == null || ready.isEnded) return
        if (visible) {
            // Revenu d'une pause : on se recale sur le direct plutôt que de reprendre où l'on était.
            player.seekTo(ready.live.playbackPositionMs(Instant.now()))
            player.playWhenReady = true
        } else {
            player.playWhenReady = false
        }
    }

    fun onDraftChange(text: String) {
        _draft.value = text.take(LIVE_CHAT_MAX_LENGTH)
    }

    fun send() {
        val text = _draft.value
        if (text.isBlank() || _isSending.value) return
        _isSending.value = true
        viewModelScope.launch {
            try {
                add(lives.send(liveId, text))
                _draft.value = ""
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Le brouillon est gardé ; un refus du serveur (débit, live terminé) est affiché tel quel.
                _errors.tryEmit(e.toUiError())
            } finally {
                _isSending.value = false
            }
        }
    }

    /** Le diffuseur termine le live : la salle est prévenue, l'écran passe à « terminé ». */
    fun endLive() {
        viewModelScope.launch {
            try {
                lives.repository.endLive(liveId)
                markEnded()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errors.tryEmit(e.toUiError())
            }
        }
    }

    /** Appelée par l'écran tant qu'il est visible : la salle se quitte quand on le quitte. */
    suspend fun collectLiveUpdates() {
        lives.repository.liveEvents(liveId).collect { event ->
            when (event) {
                LiveRoomEvent.Connected -> {
                    // Retour sur l'écran ou reconnexion : ce qui a été manqué est relu.
                    if (hasConnectedOnce) load()
                    hasConnectedOnce = true
                }
                is LiveRoomEvent.MessageReceived -> add(event.message)
                is LiveRoomEvent.ViewersChanged -> updateReady { it.copy(viewerCount = event.viewerCount) }
                LiveRoomEvent.Ended -> markEnded()
            }
        }
    }

    private fun markEnded() {
        player.stop()
        updateReady { it.copy(isEnded = true, viewerCount = 0) }
    }

    // Notre propre message arrive deux fois (réponse du POST et diffusion) : une seule ligne.
    private fun add(message: LiveChatMessage) {
        updateReady { ready ->
            if (ready.messages.any { it.id == message.id }) ready else ready.copy(messages = listOf(message) + ready.messages)
        }
    }

    private fun updateReady(change: (LiveUiState.Ready) -> LiveUiState.Ready) {
        _uiState.update { if (it is LiveUiState.Ready) change(it) else it }
    }

    override fun onCleared() {
        player.release()
    }
}
