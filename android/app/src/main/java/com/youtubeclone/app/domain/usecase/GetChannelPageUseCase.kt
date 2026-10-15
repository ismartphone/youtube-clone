package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.model.ChannelPage
import com.youtubeclone.app.domain.repository.ChannelRepository
import javax.inject.Inject

class GetChannelPageUseCase @Inject constructor(
    private val repository: ChannelRepository,
) {
    suspend operator fun invoke(channelId: String): ChannelPage = repository.getChannelPage(channelId)
}
