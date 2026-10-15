package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.model.VideoPage
import com.youtubeclone.app.domain.repository.VideoRepository
import javax.inject.Inject

/** Vidéos des chaînes suivies, de la plus récente à la plus ancienne. Exige une session ouverte. */
class GetSubscriptionFeedUseCase @Inject constructor(
    private val repository: VideoRepository,
) {
    suspend operator fun invoke(
        page: Int = 1,
        pageSize: Int = GetVideosUseCase.DEFAULT_PAGE_SIZE,
    ): VideoPage = repository.getSubscriptionFeed(page, pageSize)
}
