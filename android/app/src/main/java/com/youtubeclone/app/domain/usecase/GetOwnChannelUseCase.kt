package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.model.Channel
import com.youtubeclone.app.domain.repository.ChannelRepository
import javax.inject.Inject

class GetOwnChannelUseCase @Inject constructor(
    private val repository: ChannelRepository,
) {
    suspend operator fun invoke(): Channel? = repository.getOwnChannel()
}
