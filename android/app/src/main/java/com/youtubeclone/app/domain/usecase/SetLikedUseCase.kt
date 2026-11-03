package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.analytics.Analytics
import com.youtubeclone.app.domain.analytics.AnalyticsEvent
import com.youtubeclone.app.domain.model.LikeStatus
import com.youtubeclone.app.domain.repository.VideoRepository
import javax.inject.Inject

/** Aime une vidéo (like = true) ou retire son « j'aime » (like = false). Exige une session ouverte. */
class SetLikedUseCase @Inject constructor(
    private val repository: VideoRepository,
    private val analytics: Analytics,
) {
    suspend operator fun invoke(videoId: String, like: Boolean): LikeStatus =
        repository.setLiked(videoId, like).also {
            if (like) analytics.track(AnalyticsEvent.VideoLiked(videoId))
        }
}
