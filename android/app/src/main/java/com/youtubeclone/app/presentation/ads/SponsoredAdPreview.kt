package com.youtubeclone.app.presentation.ads

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
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
 * Repli : si le lecteur signale une erreur, ou si aucune première image n'est rendue dans les
 * [FIRST_FRAME_TIMEOUT_MS] qui suivent le début de la lecture, la vidéo est abandonnée et la
 * miniature de l'annonce reste affichée ([failed]). Une image fixe vaut mieux qu'un cadre noir ou
 * cassé en haut du fil. Cela ne détecte PAS une image corrompue produite par le décodeur : il la
 * rend sans erreur et ce serait, pour le lecteur, une première image comme une autre.
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

    // Vidéo abandonnée (erreur de lecture, ou aucune image à temps) : la miniature reste affichée.
    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    private var loadedAdId: String? = null
    private var visible = false
    private var feedShown = true

    private val handler = Handler(Looper.getMainLooper())
    private val firstFrameTimeout = Runnable { giveUp() }

    private val listener = object : Player.Listener {
        override fun onRenderedFirstFrame() {
            handler.removeCallbacks(firstFrameTimeout)
            _firstFrameShown.value = true
        }

        override fun onPlayerError(error: PlaybackException) {
            giveUp()
        }
    }

    init {
        player.addListener(listener)
    }

    /** Publicité du bandeau ; null (compte Premium, aucune publicité) arrête la lecture. */
    fun show(ad: VideoAd?) {
        if (ad == null) {
            loadedAdId = null
            handler.removeCallbacks(firstFrameTimeout)
            _failed.value = false
            player.stop()
            player.clearMediaItems()
            return
        }
        // Une annonce abandonnée est retentée quand le bandeau est rechargé, pas avant.
        if (ad.id == loadedAdId && !_failed.value) return
        loadedAdId = ad.id
        _failed.value = false
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

    /**
     * La carte entre dans l'écran ou en sort, au défilement. Hors de vue, la liste peut détruire sa
     * vue : la piste vidéo est coupée aussi, pour ne pas garder un décodeur sans surface.
     */
    fun setVisible(isVisible: Boolean) {
        if (visible == isVisible) return
        visible = isVisible
        updateVideoTrack()
        updatePlayback()
    }

    /** Fil affiché, ou quitté : autre onglet, autre écran, arrière-plan. */
    fun setFeedShown(shown: Boolean) {
        if (feedShown == shown) return
        feedShown = shown
        if (!shown) setMuted(true)
        updateVideoTrack()
        updatePlayback()
    }

    /** Bouton haut-parleur. Renvoie true si le son vient d'être activé. */
    fun toggleMute(): Boolean {
        setMuted(!_muted.value)
        return !_muted.value
    }

    fun release() {
        handler.removeCallbacks(firstFrameTimeout)
        player.removeListener(listener)
        player.release()
    }

    // Abandon de la vidéo : lecture arrêtée et décodeur libéré, la miniature reste affichée.
    private fun giveUp() {
        handler.removeCallbacks(firstFrameTimeout)
        if (loadedAdId == null || _failed.value) return
        _failed.value = true
        player.stop()
        player.clearMediaItems()
    }

    // Le compte à rebours ne court que pendant une lecture voulue (carte visible, fil affiché) qui
    // n'a pas encore rendu d'image : hors de vue, la piste vidéo est coupée et aucune image ne vient.
    private fun updateFirstFrameTimeout() {
        handler.removeCallbacks(firstFrameTimeout)
        val waiting = loadedAdId != null && visible && feedShown && !_firstFrameShown.value && !_failed.value
        if (waiting) handler.postDelayed(firstFrameTimeout, FIRST_FRAME_TIMEOUT_MS)
    }

    private fun setMuted(muted: Boolean) {
        _muted.value = muted
        player.volume = if (muted) 0f else 1f
    }

    // Image seulement quand la carte est visible sur le fil affiché ; sinon le décodeur est libéré,
    // et la miniature recouvre le cadre jusqu'à la première image au retour.
    private fun updateVideoTrack() {
        val showVideo = visible && feedShown
        if (!showVideo) _firstFrameShown.value = false
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !showVideo)
            .build()
    }

    private fun updatePlayback() {
        player.playWhenReady = loadedAdId != null && visible && feedShown && !_failed.value
        updateFirstFrameTimeout()
    }

    private companion object {
        // Une première image HLS arrive en quelques secondes. 8 s laissent de la marge à un réseau
        // lent, et l'attente ne coûte rien à l'écran : la miniature recouvre déjà le cadre.
        const val FIRST_FRAME_TIMEOUT_MS = 8_000L
    }
}
