package com.youtubeclone.app.presentation.ads

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.youtubeclone.app.domain.model.VideoAd
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Vidéo du bandeau sponsorisé en haut du fil (ADR-021) : en boucle et en sourdine, lue seulement
 * quand la carte est visible et que le fil est affiché. Une vidéo qui tournerait hors de vue ferait
 * ramer le défilement. Fil quitté (autre onglet, autre écran, arrière-plan) : retour en sourdine et
 * piste vidéo coupée, ce qui libère son décodeur ; gardé pendant que la surface d'affichage est
 * détruite puis recréée, il afficherait une image corrompue au retour.
 * Créé par un ViewModel ; [release] dans onCleared.
 */
class SponsoredAdPreview(context: Context) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        repeatMode = Player.REPEAT_MODE_ONE
        volume = 0f
    }

    private val _muted = MutableStateFlow(true)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    // Première image affichée : avant, la miniature recouvre la vue vidéo, noire jusque-là.
    private val _firstFrameShown = MutableStateFlow(false)
    val firstFrameShown: StateFlow<Boolean> = _firstFrameShown.asStateFlow()

    private var loadedAdId: String? = null
    private var visible = false
    private var feedShown = true

    private val listener = object : Player.Listener {
        override fun onRenderedFirstFrame() {
            _firstFrameShown.value = true
        }
    }

    init {
        player.addListener(listener)
    }

    /** Publicité du bandeau ; null (compte Premium, aucune publicité) arrête la lecture. */
    fun show(ad: VideoAd?) {
        if (ad == null) {
            loadedAdId = null
            player.stop()
            player.clearMediaItems()
            return
        }
        if (ad.id == loadedAdId) return
        loadedAdId = ad.id
        _firstFrameShown.value = false
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(ad.streamUrl)
                .setMimeType(if (ad.isHls) MimeTypes.APPLICATION_M3U8 else null)
                .build(),
        )
        player.prepare()
        updatePlayback()
    }

    /** La carte entre dans l'écran ou en sort, au défilement. */
    fun setVisible(isVisible: Boolean) {
        if (visible == isVisible) return
        visible = isVisible
        updatePlayback()
    }

    /** Fil affiché, ou quitté : autre onglet, autre écran, arrière-plan. */
    fun setFeedShown(shown: Boolean) {
        if (feedShown == shown) return
        feedShown = shown
        if (!shown) {
            setMuted(true)
            _firstFrameShown.value = false
        }
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !shown)
            .build()
        updatePlayback()
    }

    /** Bouton haut-parleur. Renvoie true si le son vient d'être activé. */
    fun toggleMute(): Boolean {
        setMuted(!_muted.value)
        return !_muted.value
    }

    fun release() {
        player.removeListener(listener)
        player.release()
    }

    private fun setMuted(muted: Boolean) {
        _muted.value = muted
        player.volume = if (muted) 0f else 1f
    }

    private fun updatePlayback() {
        player.playWhenReady = loadedAdId != null && visible && feedShown
    }
}
