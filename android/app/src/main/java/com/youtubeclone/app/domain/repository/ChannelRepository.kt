package com.youtubeclone.app.domain.repository

import com.youtubeclone.app.domain.model.ChannelPage
import com.youtubeclone.app.domain.model.FollowChange
import com.youtubeclone.app.domain.model.FollowStatus
import kotlinx.coroutines.flow.Flow

/** Chaînes et abonnements gratuits. Les implémentations lèvent une [com.youtubeclone.app.domain.model.DataError] en cas d'échec. */
interface ChannelRepository {
    /** Émet chaque abonnement ou désabonnement réussi, quel que soit l'écran qui l'a demandé. */
    val followChanges: Flow<FollowChange>

    suspend fun getChannelPage(channelId: String): ChannelPage

    /** S'abonne (true) ou se désabonne (false). Exige une session ouverte. */
    suspend fun setFollowing(channelId: String, follow: Boolean): FollowStatus
}
