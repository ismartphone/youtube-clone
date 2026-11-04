package com.youtubeclone.app

import android.app.Application
import com.youtubeclone.app.data.analytics.PostHogAnalytics
import com.youtubeclone.app.notifications.PushTokenSync
import com.youtubeclone.app.playback.download.DownloadsSync
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class YoutubeCloneApp : Application() {

    @Inject lateinit var analytics: PostHogAnalytics
    @Inject lateinit var downloadsSync: DownloadsSync
    @Inject lateinit var pushTokenSync: PushTokenSync

    override fun onCreate() {
        super.onCreate()
        analytics.start()
        downloadsSync.start()
        pushTokenSync.start()
    }
}
