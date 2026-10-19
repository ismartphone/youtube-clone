package com.youtubeclone.app.data.remote.dto

import com.youtubeclone.app.domain.model.Channel
import com.youtubeclone.app.domain.model.ChannelPage
import com.youtubeclone.app.domain.model.FollowStatus
import kotlinx.serialization.Serializable

// Réponses du module channels du backend (channel.presenter.ts).

@Serializable
data class FollowStatusDto(
    val followerCount: Int,
    val isFollowing: Boolean,
)

@Serializable
data class ChannelPageDto(
    val id: String,
    val name: String,
    val handle: String,
    val avatarUrl: String? = null,
    val description: String? = null,
    val followerCount: Int,
    val isFollowing: Boolean,
    val isOwner: Boolean = false,
    val videos: List<VideoSummaryDto>,
)

@Serializable
data class OwnChannelDto(
    val channel: ChannelSummaryDto? = null,
)

fun FollowStatusDto.toDomain() = FollowStatus(followerCount = followerCount, isFollowing = isFollowing)

fun ChannelPageDto.toDomain() = ChannelPage(
    channel = Channel(id = id, name = name, handle = handle, avatarUrl = avatarUrl),
    description = description,
    followStatus = FollowStatus(followerCount = followerCount, isFollowing = isFollowing),
    isOwner = isOwner,
    videos = videos.map { it.toDomain() },
)
