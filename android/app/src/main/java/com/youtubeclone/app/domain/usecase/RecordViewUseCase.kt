package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.analytics.Analytics
import com.youtubeclone.app.domain.analytics.AnalyticsEvent
import com.youtubeclone.app.domain.model.Video
import com.youtubeclone.app.domain.repository.VideoRepository
import javax.inject.Inject

/**
 * Une lecture a commencé : mesure envoyée à PostHog, puis signalement au serveur pour le
 * compteur de vues. La mesure part même si le serveur ne répond pas.
 */
class RecordViewUseCase @Inject constructor(
    private val repository: VideoRepository,
    private val analytics: Analytics,
) {
    suspend operator fun invoke(video: Video) {
        analytics.track(AnalyticsEvent.VideoPlayed(video))
        repository.recordView(video.id)
    }
}
