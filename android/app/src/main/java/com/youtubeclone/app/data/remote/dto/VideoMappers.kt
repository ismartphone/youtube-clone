package com.youtubeclone.app.data.remote.dto

import com.youtubeclone.app.data.remote.resolveMediaUrl
import com.youtubeclone.app.domain.model.Category
import com.youtubeclone.app.domain.model.Channel
import com.youtubeclone.app.domain.model.LikeStatus
import com.youtubeclone.app.domain.model.Video
import com.youtubeclone.app.domain.model.VideoDetail
import com.youtubeclone.app.domain.model.VideoFormat
import com.youtubeclone.app.domain.model.VideoPage
import java.time.Instant

fun ChannelSummaryDto.toDomain() = Channel(
    id = id,
    name = name,
    handle = handle,
    avatarUrl = avatarUrl,
)

fun CategoryDto.toDomain() = Category(id = id, name = name)

fun VideoSummaryDto.toDomain() = Video(
    id = id,
    title = title,
    thumbnailUrl = resolveMediaUrl(thumbnailUrl),
    durationSeconds = durationSeconds,
    format = VideoFormat.valueOf(format),
    isPremium = isPremium,
    viewCount = viewCount,
    publishedAt = Instant.parse(publishedAt),
    channel = channel.toDomain(),
    category = category?.toDomain(),
)

fun VideoDetailDto.toDomain() = VideoDetail(
    video = Video(
        id = id,
        title = title,
        thumbnailUrl = resolveMediaUrl(thumbnailUrl),
        durationSeconds = durationSeconds,
        format = VideoFormat.valueOf(format),
        isPremium = isPremium,
        viewCount = viewCount,
        publishedAt = Instant.parse(publishedAt),
        channel = channel.toDomain(),
        category = category?.toDomain(),
    ),
    description = description,
    // Vidéo hébergée chez nous : chemin relatif, complété comme les miniatures.
    videoUrl = videoUrl?.let(::resolveMediaUrl),
    likeStatus = LikeStatus(likeCount = likeCount, isLiked = isLiked),
)

fun LikeStatusDto.toDomain() = LikeStatus(likeCount = likeCount, isLiked = isLiked)

fun VideoPageDto.toDomain() = VideoPage(
    items = items.map { it.toDomain() },
    page = page,
    pageSize = pageSize,
    total = total,
)
