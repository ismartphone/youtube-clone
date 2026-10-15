package com.youtubeclone.app.domain.repository

import com.youtubeclone.app.domain.model.VideoDetail
import com.youtubeclone.app.domain.model.VideoFormat
import com.youtubeclone.app.domain.model.VideoPage

/** Accès au catalogue. Les implémentations lèvent une [com.youtubeclone.app.domain.model.DataError] en cas d'échec. */
interface VideoRepository {
    suspend fun getVideos(
        format: VideoFormat?,
        categoryId: String?,
        page: Int,
        pageSize: Int,
    ): VideoPage

    suspend fun getVideoDetail(id: String): VideoDetail

    suspend fun recordView(id: String)
}
