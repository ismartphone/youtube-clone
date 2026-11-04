package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.model.FollowStatus
import com.youtubeclone.app.domain.model.NotificationLevel
import com.youtubeclone.app.domain.repository.ChannelRepository
import javax.inject.Inject

/** Règle la cloche d'un abonnement (ADR-020) : Toutes, Personnalisées ou Aucune. */
class SetNotificationLevelUseCase @Inject constructor(
    private val repository: ChannelRepository,
) {
    suspend operator fun invoke(channelId: String, level: NotificationLevel, current: FollowStatus): FollowStatus =
        repository.setNotificationLevel(channelId, level, current)
}
