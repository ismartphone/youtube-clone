package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.model.LikeStatus
import com.youtubeclone.app.domain.repository.VideoRepository
import javax.inject.Inject

/** Aime une vidéo (like = true) ou retire son « j'aime » (like = false). Exige une session ouverte. */
class SetLikedUseCase @Inject constructor(
    private val repository: VideoRepository,
) {
    suspend operator fun invoke(videoId: String, like: Boolean): LikeStatus = repository.setLiked(videoId, like)
}
