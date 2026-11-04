package com.youtubeclone.app.data.remote.dto

import com.youtubeclone.app.data.remote.resolveMediaUrl
import com.youtubeclone.app.domain.model.Channel
import com.youtubeclone.app.domain.model.ChannelPage
import com.youtubeclone.app.domain.model.ChannelProfile
import com.youtubeclone.app.domain.model.FollowStatus
import com.youtubeclone.app.domain.model.NotificationLevel
import kotlinx.serialization.Serializable

// Réponses du module channels du backend (channel.presenter.ts).

@Serializable
data class FollowStatusDto(
    val followerCount: Int,
    val isFollowing: Boolean,
    val notificationLevel: String? = null,
)

/** Corps et réponse de PATCH /channels/{id}/follow : la cloche (ADR-020). */
@Serializable
data class NotificationLevelDto(
    val notificationLevel: String,
)

@Serializable
data class ChannelPageDto(
    val id: String,
    val name: String,
    val handle: String,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val description: String? = null,
    val followerCount: Int,
    val isFollowing: Boolean,
    val notificationLevel: String? = null,
    val isOwner: Boolean = false,
    val videos: List<VideoSummaryDto>,
)

@Serializable
data class CreateChannelRequestDto(
    val name: String,
    val handle: String,
    val description: String? = null,
)

/** Corps de PATCH /me/channel (ADR-018) : un champ null n'est pas envoyé, donc pas modifié. */
@Serializable
data class UpdateChannelRequestDto(
    val name: String? = null,
    val handle: String? = null,
    val description: String? = null,
)

/** Chaîne de l'utilisateur après une personnalisation : réponse des routes /me/channel. */
@Serializable
data class ChannelProfileDto(
    val id: String,
    val name: String,
    val handle: String,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val description: String? = null,
)

fun ChannelProfileDto.toDomain() = ChannelProfile(
    channel = Channel(id = id, name = name, handle = handle, avatarUrl = avatarUrl?.let(::resolveMediaUrl)),
    bannerUrl = bannerUrl?.let(::resolveMediaUrl),
    description = description,
)

@Serializable
data class OwnChannelDto(
    val channel: ChannelSummaryDto? = null,
)

// Valeurs du backend : ALL, PERSONALIZED, NONE. Une valeur inconnue vaut le réglage par défaut.
fun notificationLevelOf(value: String?): NotificationLevel? = when (value) {
    null -> null
    "ALL" -> NotificationLevel.All
    "NONE" -> NotificationLevel.None
    else -> NotificationLevel.Personalized
}

val NotificationLevel.apiValue: String
    get() = when (this) {
        NotificationLevel.All -> "ALL"
        NotificationLevel.Personalized -> "PERSONALIZED"
        NotificationLevel.None -> "NONE"
    }

fun FollowStatusDto.toDomain() =
    FollowStatus(followerCount = followerCount, isFollowing = isFollowing, notificationLevel = notificationLevelOf(notificationLevel))

fun ChannelPageDto.toDomain() = ChannelPage(
    channel = Channel(id = id, name = name, handle = handle, avatarUrl = avatarUrl?.let(::resolveMediaUrl)),
    bannerUrl = bannerUrl?.let(::resolveMediaUrl),
    description = description,
    followStatus = FollowStatus(followerCount, isFollowing, notificationLevelOf(notificationLevel)),
    isOwner = isOwner,
    videos = videos.map { it.toDomain() },
)
