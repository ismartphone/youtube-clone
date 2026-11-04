package com.youtubeclone.app.presentation.ads

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.youtubeclone.app.domain.analytics.Analytics
import com.youtubeclone.app.domain.analytics.AnalyticsEvent
import com.youtubeclone.app.domain.model.VideoAd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** L'annonce peut être ignorée au bout de 5 secondes (ADR-021). */
const val AD_SKIP_AFTER_MS = 5_000L

private const val PROGRESS_TICK_MS = 200L

/** Annonce en cours de lecture. */
data class AdPlaybackState(
    val ad: VideoAd,
    val positionMs: Long,
    val durationMs: Long,
) {
    val canSkip: Boolean get() = positionMs >= AD_SKIP_AFTER_MS

    /** Secondes avant « Ignorer », arrondies au-dessus : 5, 4… 1. */
    val secondsUntilSkip: Int get() = ((AD_SKIP_AFTER_MS - positionMs + 999) / 1000).toInt().coerceAtLeast(0)

    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    val secondsLeft: Int get() = ((durationMs - positionMs + 999) / 1000).toInt().coerceAtLeast(0)
}

/**
 * Lecture d'une publicité (ADR-021), dans un lecteur à elle : celui de la vidéo reste prêt,
 * en pause. Aucun contrôle de lecture : ni pause, ni déplacement dans la barre. Ignorée après
 * 5 secondes ou terminée, elle appelle le onFinished donné à [play]. Une publicité illisible
 * le fait tout de suite : elle ne doit jamais empêcher la vidéo.
 * Créé par un ViewModel avec son viewModelScope ; [release] dans onCleared.
 */
class AdPlaybackController(
    context: Context,
    private val scope: CoroutineScope,
    private val analytics: Analytics,
) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    private val _state = MutableStateFlow<AdPlaybackState?>(null)

    /** Null quand aucune annonce n'est en cours. */
    val state: StateFlow<AdPlaybackState?> = _state.asStateFlow()

    private var onFinished: (() -> Unit)? = null
    private var ticker: Job? = null

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                _state.value?.let { analytics.track(AnalyticsEvent.AdCompleted(it.ad.id)) }
                finish()
            }
        }

        override fun onPlayerError(error: PlaybackException) = finish()
    }

    init {
        player.addListener(listener)
    }

    fun play(ad: VideoAd, onFinished: () -> Unit) {
        this.onFinished = onFinished
        _state.value = AdPlaybackState(ad, positionMs = 0, durationMs = ad.durationSeconds * 1000L)
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(ad.streamUrl)
                .setMimeType(if (ad.isHls) MimeTypes.APPLICATION_M3U8 else null)
                .build(),
        )
        player.prepare()
        player.playWhenReady = true
        analytics.track(AnalyticsEvent.AdStarted(ad.id))
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val duration = player.duration.takeIf { it > 0 }
                _state.value = _state.value?.let {
                    it.copy(positionMs = player.currentPosition, durationMs = duration ?: it.durationMs)
                } ?: break
                delay(PROGRESS_TICK_MS)
            }
        }
    }

    /** Bouton « Ignorer », actif au bout de 5 secondes. */
    fun skip() {
        val current = _state.value ?: return
        if (!current.canSkip) return
        analytics.track(AnalyticsEvent.AdSkipped(current.ad.id, elapsedSeconds = (player.currentPosition / 1000).toInt()))
        finish()
    }

    /** Lien « Supprimer les publicités » affiché pendant l'annonce avant une vidéo. */
    fun onRemoveAdsClicked() {
        _state.value?.let { analytics.track(AnalyticsEvent.RemoveAdsClickedPreroll(it.ad.id)) }
    }

    /** Abonnement Premium pris pendant l'annonce : elle s'arrête, sans mesure. */
    fun cancel() {
        if (_state.value != null) finish()
    }

    /** Écran masqué : l'annonce attend, elle ne se joue jamais en arrière-plan. */
    fun pause() = player.pause()

    fun resume() {
        if (_state.value != null) player.play()
    }

    fun release() {
        ticker?.cancel()
        player.removeListener(listener)
        player.release()
    }

    private fun finish() {
        ticker?.cancel()
        player.stop()
        player.clearMediaItems()
        _state.value = null
        val callback = onFinished
        onFinished = null
        callback?.invoke()
    }
}
