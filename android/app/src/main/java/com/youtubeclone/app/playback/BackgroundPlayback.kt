package com.youtubeclone.app.playback

import android.content.Context
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lecture en arrière-plan, avantage Premium (ADR-007). Le lecteur reste celui de l'écran de
 * lecture ; une session média le confie à [PlaybackService], qui garde l'application active
 * et affiche la notification de lecture quand on la quitte.
 */
@Singleton
class BackgroundPlayback @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Session du lecteur en cours, lue par le service. */
    var session: MediaSession? = null
        private set

    fun start(player: Player) {
        if (session?.player === player) return
        stop()
        session = MediaSession.Builder(context, player).build()
        // Démarré pendant que l'écran de lecture est affiché : l'application est au premier plan.
        context.startService(Intent(context, PlaybackService::class.java))
    }

    /** Arrête la lecture en arrière-plan ; avec [player], seulement si c'est le sien. */
    fun stop(player: Player? = null) {
        val current = session ?: return
        if (player != null && current.player !== player) return
        session = null
        current.release()
        context.stopService(Intent(context, PlaybackService::class.java))
    }
}
