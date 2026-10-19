package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.model.VideoPage
import com.youtubeclone.app.domain.repository.VideoRepository
import javax.inject.Inject

/** Historique de visionnage, de la vidéo vue le plus récemment à la plus ancienne. Exige une session ouverte. */
class GetWatchHistoryUseCase @Inject constructor(
    private val repository: VideoRepository,
) {
    suspend operator fun invoke(page: Int, pageSize: Int = GetVideosUseCase.DEFAULT_PAGE_SIZE): VideoPage =
        repository.getWatchHistory(page, pageSize)
}

/** Vidéos aimées, de la plus récemment aimée à la plus ancienne. Exige une session ouverte. */
class GetLikedVideosUseCase @Inject constructor(
    private val repository: VideoRepository,
) {
    suspend operator fun invoke(page: Int, pageSize: Int = GetVideosUseCase.DEFAULT_PAGE_SIZE): VideoPage =
        repository.getLikedVideos(page, pageSize)
}
