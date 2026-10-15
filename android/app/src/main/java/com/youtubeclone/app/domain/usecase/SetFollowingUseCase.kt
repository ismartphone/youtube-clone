package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.model.FollowStatus
import com.youtubeclone.app.domain.repository.ChannelRepository
import javax.inject.Inject

/** S'abonne à une chaîne (follow = true) ou s'en désabonne (follow = false). */
class SetFollowingUseCase @Inject constructor(
    private val repository: ChannelRepository,
) {
    suspend operator fun invoke(channelId: String, follow: Boolean): FollowStatus =
        repository.setFollowing(channelId, follow)
}
