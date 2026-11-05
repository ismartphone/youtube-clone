package com.youtubeclone.app

import android.app.Application
import com.youtubeclone.app.data.analytics.PostHogAnalytics
import com.youtubeclone.app.data.server.ServerAddress
import com.youtubeclone.app.domain.usecase.UnreadMessages
import com.youtubeclone.app.notifications.PushTokenSync
import com.youtubeclone.app.playback.download.DownloadsSync
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class YoutubeCloneApp : Application() {

    @Inject lateinit var analytics: PostHogAnalytics
    @Inject lateinit var downloadsSync: DownloadsSync
    @Inject lateinit var pushTokenSync: PushTokenSync
    @Inject lateinit var unreadMessages: UnreadMessages

    override fun onCreate() {
        // Avant super.onCreate() : Hilt y crée les singletons réseau, qui lisent l'adresse du serveur (ADR-027).
        ServerAddress.initialize(this)
        super.onCreate()
        analytics.start()
        downloadsSync.start()
        pushTokenSync.start()
        unreadMessages.start()
    }
}
