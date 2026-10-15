package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.model.LiveVideoEvent
import com.youtubeclone.app.domain.repository.LiveVideoRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveLiveVideoUseCase @Inject constructor(
    private val repository: LiveVideoRepository,
) {
    operator fun invoke(videoId: String): Flow<LiveVideoEvent> = repository.videoEvents(videoId)
}
