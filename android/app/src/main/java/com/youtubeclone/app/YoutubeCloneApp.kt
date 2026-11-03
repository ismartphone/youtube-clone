package com.youtubeclone.app

import android.app.Application
import com.youtubeclone.app.data.analytics.PostHogAnalytics
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class YoutubeCloneApp : Application() {

    @Inject lateinit var analytics: PostHogAnalytics

    override fun onCreate() {
        super.onCreate()
        analytics.start()
    }
}
