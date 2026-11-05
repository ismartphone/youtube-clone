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
import com.youtubeclone.app.domain.model.LiveProduct
import com.youtubeclone.app.domain.model.PaymentMode
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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Paiement Stripe à présenter dans la PaymentSheet, une seule fois (ADR-029, étape 3). */
data class LiveStripePayment(val publishableKey: String, val clientSecret: String)

/** Achat en cours du produit épinglé. */
data class LivePurchaseState(
    val isBusy: Boolean = false,
    /** Mode Stripe : à présenter dans la PaymentSheet puis remis à null. */
    val stripePayment: LiveStripePayment? = null,
    /** Mode simulé (repli) : jeton à confirmer depuis la feuille de paiement simulée. */
    val simulatedToken: String? = null,
)

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
        /** Produit épinglé par le diffuseur, visible chez tous ; null sans épinglage. */
        val pinnedProduct: LiveProduct? = null,
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

    /**
     * Réactions reçues de la salle, une par émoji à faire remonter à l'écran. Rien n'est gardé :
     * une rafale qui dépasse le tampon perd ses plus anciennes (un écran saturé n'aide personne).
     */
    private val _reactions = MutableSharedFlow<String>(extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val reactions: SharedFlow<String> = _reactions.asSharedFlow()

    private val _ownProducts = MutableStateFlow<List<LiveProduct>?>(null)

    /** Produits de la chaîne du diffuseur, lus à l'ouverture du choix ; null tant qu'ils ne le sont pas. */
    val ownProducts: StateFlow<List<LiveProduct>?> = _ownProducts.asStateFlow()

    private val _purchase = MutableStateFlow(LivePurchaseState())
    val purchase: StateFlow<LivePurchaseState> = _purchase.asStateFlow()

    // Référence du paiement Stripe en cours, renvoyée au serveur une fois la PaymentSheet réglée.
    private var pendingStripeToken: String? = null

    private val _notices = MutableSharedFlow<Int>(extraBufferCapacity = 1)

    /** Messages d'information (achat confirmé), par identifiant de texte. */
    val notices: SharedFlow<Int> = _notices.asSharedFlow()

    /** Lecteur du flux, propre à cet écran : le même fichier en boucle, comme un direct. */
    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        repeatMode = Player.REPEAT_MODE_ALL
    }

    // Dernier nombre reçu par la salle : plus récent que celui d'une lecture REST partie avant notre entrée.
    private var socketViewers: Int? = null
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
                    viewerCount = if (live.isLive) socketViewers ?: live.viewerCount else 0,
                    isEnded = !live.isLive,
                    pinnedProduct = if (live.isLive) live.pinnedProduct else null,
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

    /**
     * Envoie une réaction : elle revient par la salle, pour tous, auteur compris (pas d'écho local,
     * sinon elle monterait deux fois). Un refus (débit, live terminé) est ignoré en silence : un
     * message d'erreur par émoji serait plus gênant que la réaction perdue.
     */
    fun react(emoji: String) {
        viewModelScope.launch {
            try {
                lives.repository.sendReaction(liveId, emoji)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    /** Le diffuseur ouvre le choix du produit à épingler : ses produits sont relus à chaque ouverture. */
    fun loadOwnProducts() {
        viewModelScope.launch {
            try {
                _ownProducts.value = lives.repository.getMyProducts()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ownProducts.value = emptyList()
                _errors.tryEmit(e.toUiError())
            }
        }
    }

    /** Épingle [product] : la carte apparaît chez tous, auteur compris, par la salle. */
    fun pin(product: LiveProduct) {
        viewModelScope.launch {
            try {
                val pinned = lives.repository.pinProduct(liveId, product.id)
                updateReady { it.copy(pinnedProduct = pinned) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errors.tryEmit(e.toUiError())
            }
        }
    }

    fun unpin() {
        viewModelScope.launch {
            try {
                lives.repository.unpinProduct(liveId)
                updateReady { it.copy(pinnedProduct = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errors.tryEmit(e.toUiError())
            }
        }
    }

    /**
     * Étape 1 de l'achat (ADR-029) : le serveur démarre le paiement du produit épinglé, à son prix.
     * En mode Stripe, la PaymentSheet s'ouvre ; en mode simulé (repli), la feuille de paiement simulée.
     */
    fun startPurchase() {
        if (_purchase.value.isBusy) return
        _purchase.value = LivePurchaseState(isBusy = true)
        viewModelScope.launch {
            try {
                val checkout = lives.repository.startCheckout(liveId)
                when (checkout.mode) {
                    PaymentMode.SIMULATED -> _purchase.value = LivePurchaseState(simulatedToken = checkout.purchaseToken)
                    PaymentMode.STRIPE -> {
                        val key = checkout.publishableKey
                        val secret = checkout.clientSecret
                        if (key == null || secret == null) {
                            _purchase.value = LivePurchaseState()
                            _errors.tryEmit(UiError(R.string.live_purchase_failed))
                            return@launch
                        }
                        pendingStripeToken = checkout.purchaseToken
                        _purchase.value = LivePurchaseState(isBusy = true, stripePayment = LiveStripePayment(key, secret))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _purchase.value = LivePurchaseState()
                // Live terminé, produit retiré, auto-achat : le message du serveur est affiché tel quel.
                _errors.tryEmit(e.toUiError())
            }
        }
    }

    /** La PaymentSheet est affichée : elle ne doit pas être présentée une seconde fois. */
    fun onStripeSheetShown() {
        _purchase.update { it.copy(stripePayment = null) }
    }

    /** Fin de la PaymentSheet : réglée, le serveur vérifie le paiement auprès de Stripe avant d'enregistrer l'achat. */
    fun onStripeOutcome(completed: Boolean, canceled: Boolean) {
        val token = pendingStripeToken
        pendingStripeToken = null
        if (completed && token != null) {
            confirm(token)
            return
        }
        _purchase.value = LivePurchaseState()
        _errors.tryEmit(UiError(if (canceled) R.string.live_purchase_canceled else R.string.live_purchase_failed))
    }

    /** Mode simulé, étape 2 : le serveur vérifie le jeton et enregistre l'achat. */
    fun confirmSimulated() {
        val token = _purchase.value.simulatedToken ?: return
        if (_purchase.value.isBusy) return
        confirm(token)
    }

    /** Feuille simulée fermée sans confirmer : le jeton expirera de lui-même côté serveur. */
    fun dismissSimulated() {
        if (_purchase.value.isBusy) return
        _purchase.value = LivePurchaseState()
    }

    private fun confirm(token: String) {
        _purchase.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                lives.repository.confirmPurchase(liveId, token)
                _notices.tryEmit(R.string.live_purchase_done)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errors.tryEmit(e.toUiError())
            } finally {
                _purchase.value = LivePurchaseState()
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
                is LiveRoomEvent.ReactionReceived -> _reactions.tryEmit(event.emoji)
                is LiveRoomEvent.ViewersChanged -> {
                    socketViewers = event.viewerCount
                    updateReady { it.copy(viewerCount = event.viewerCount) }
                }
                is LiveRoomEvent.ProductPinned -> updateReady { it.copy(pinnedProduct = event.product) }
                LiveRoomEvent.Ended -> markEnded()
            }
        }
    }

    private fun markEnded() {
        socketViewers = 0
        player.stop()
        updateReady { it.copy(isEnded = true, viewerCount = 0, pinnedProduct = null) }
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
