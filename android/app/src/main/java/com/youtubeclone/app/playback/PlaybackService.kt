package com.youtubeclone.app.playback

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Service de lecture des comptes Premium. Media3 le passe au premier plan, avec sa notification,
 * tant que la vidéo joue : le son continue application quittée.
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var playback: BackgroundPlayback

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        playback.session?.let { if (!isSessionAdded(it)) addSession(it) }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = playback.session

    // Application fermée depuis les tâches récentes : la lecture s'arrête avec elle.
    override fun onTaskRemoved(rootIntent: Intent?) {
        playback.session?.player?.pause()
        playback.stop()
        stopSelf()
    }
}
