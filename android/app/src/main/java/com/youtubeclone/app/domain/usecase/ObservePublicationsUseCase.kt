package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.model.Publication
import com.youtubeclone.app.domain.repository.PublicationFeed
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObservePublicationsUseCase @Inject constructor(
    private val feed: PublicationFeed,
) {
    operator fun invoke(): Flow<Publication> = feed.publications
}
