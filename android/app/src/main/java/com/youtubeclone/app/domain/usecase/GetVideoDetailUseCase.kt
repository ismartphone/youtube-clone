package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.model.VideoDetail
import com.youtubeclone.app.domain.repository.VideoRepository
import javax.inject.Inject

class GetVideoDetailUseCase @Inject constructor(
    private val repository: VideoRepository,
) {
    suspend operator fun invoke(id: String): VideoDetail = repository.getVideoDetail(id)
}
