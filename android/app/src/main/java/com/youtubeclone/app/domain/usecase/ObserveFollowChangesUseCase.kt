package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.model.FollowChange
import com.youtubeclone.app.domain.repository.ChannelRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveFollowChangesUseCase @Inject constructor(
    private val repository: ChannelRepository,
) {
    operator fun invoke(): Flow<FollowChange> = repository.followChanges
}
