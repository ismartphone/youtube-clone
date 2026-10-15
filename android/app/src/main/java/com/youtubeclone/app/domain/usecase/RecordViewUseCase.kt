package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.repository.VideoRepository
import javax.inject.Inject

/** Signale au serveur qu'une lecture a commencé, pour le compteur de vues. */
class RecordViewUseCase @Inject constructor(
    private val repository: VideoRepository,
) {
    suspend operator fun invoke(videoId: String) = repository.recordView(videoId)
}
