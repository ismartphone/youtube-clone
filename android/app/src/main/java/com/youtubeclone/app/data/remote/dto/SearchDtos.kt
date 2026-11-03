package com.youtubeclone.app.data.remote.dto

import com.youtubeclone.app.domain.model.Channel
import com.youtubeclone.app.domain.model.ChannelSearchResult
import com.youtubeclone.app.domain.model.SearchPage
import kotlinx.serialization.Serializable

// Réponse de GET /search (SearchController du backend).

@Serializable
data class ChannelSearchResultDto(
    val id: String,
    val name: String,
    val handle: String,
    val avatarUrl: String? = null,
    val followerCount: Int,
)

@Serializable
data class ChannelSearchPageDto(
    val items: List<ChannelSearchResultDto>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
)

@Serializable
data class SearchResponseDto(
    val videos: VideoPageDto,
    val channels: ChannelSearchPageDto,
)

fun ChannelSearchResultDto.toDomain() = ChannelSearchResult(
    channel = Channel(id = id, name = name, handle = handle, avatarUrl = avatarUrl),
    followerCount = followerCount,
)

fun SearchResponseDto.toDomain() = SearchPage(
    videos = videos.toDomain(),
    channels = channels.items.map { it.toDomain() },
    channelTotal = channels.total,
)
