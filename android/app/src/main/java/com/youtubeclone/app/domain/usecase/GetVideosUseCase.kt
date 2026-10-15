package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.model.VideoFormat
import com.youtubeclone.app.domain.model.VideoPage
import com.youtubeclone.app.domain.repository.VideoRepository
import javax.inject.Inject

/** Liste les vidéos, de la plus récente à la plus ancienne (tri fait par l'API). */
class GetVideosUseCase @Inject constructor(
    private val repository: VideoRepository,
) {
    suspend operator fun invoke(
        format: VideoFormat? = null,
        categoryId: String? = null,
        page: Int = 1,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): VideoPage = repository.getVideos(format, categoryId, page, pageSize)

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}
