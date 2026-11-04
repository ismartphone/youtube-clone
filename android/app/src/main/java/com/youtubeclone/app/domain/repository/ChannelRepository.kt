package com.youtubeclone.app.domain.repository

import com.youtubeclone.app.domain.model.Channel
import com.youtubeclone.app.domain.model.ChannelImageKind
import com.youtubeclone.app.domain.model.ChannelPage
import com.youtubeclone.app.domain.model.ChannelProfile
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

    /** Émet la chaîne de l'utilisateur après chaque personnalisation (ADR-018). */
    val ownChannelUpdated: Flow<ChannelProfile>

    /**
     * Nom, handle et description de sa chaîne ; un paramètre null n'est pas modifié. Lève
     * [com.youtubeclone.app.domain.model.DataError.Server] (409) si le handle est déjà pris.
     */
    suspend fun updateOwnChannel(name: String?, handle: String?, description: String?): ChannelProfile

    /** Taille en octets de l'image [uri] choisie dans la galerie, ou null si elle est illisible. */
    suspend fun imageSize(uri: String): Long?

    /** Photo de profil ou bannière choisie dans la galerie ; le serveur la recadre. */
    suspend fun setOwnChannelImage(kind: ChannelImageKind, uri: String): ChannelProfile

    /** Retire la photo (retour à l'avatar généré) ou la bannière. */
    suspend fun removeOwnChannelImage(kind: ChannelImageKind): ChannelProfile

    /** S'abonne (true) ou se désabonne (false). Exige une session ouverte. */
    suspend fun setFollowing(channelId: String, follow: Boolean): FollowStatus
}
