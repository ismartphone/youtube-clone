package com.youtubeclone.app.domain.repository

import com.youtubeclone.app.domain.model.Channel
import com.youtubeclone.app.domain.model.ChannelPage
import com.youtubeclone.app.domain.model.FollowChange
import com.youtubeclone.app.domain.model.FollowStatus
import kotlinx.coroutines.flow.Flow

/** Chaînes et abonnements gratuits. Les implémentations lèvent une [com.youtubeclone.app.domain.model.DataError] en cas d'échec. */
interface ChannelRepository {
    /** Émet chaque abonnement ou désabonnement réussi, quel que soit l'écran qui l'a demandé. */
    val followChanges: Flow<FollowChange>

    suspend fun getChannelPage(channelId: String): ChannelPage

    /** Chaîne de l'utilisateur connecté, ou null s'il n'en a pas. Exige une session ouverte. */
    suspend fun getOwnChannel(): Channel?

    /** Émet la chaîne de l'utilisateur dès qu'il l'a créée. */
    val ownChannelCreated: Flow<Channel>

    /**
     * Crée la chaîne de l'utilisateur connecté. Lève [com.youtubeclone.app.domain.model.DataError.Server]
     * (409) si le handle est déjà pris ou si l'utilisateur a déjà une chaîne.
     */
    suspend fun createChannel(name: String, handle: String, description: String?): Channel

    /** S'abonne (true) ou se désabonne (false). Exige une session ouverte. */
    suspend fun setFollowing(channelId: String, follow: Boolean): FollowStatus
}
