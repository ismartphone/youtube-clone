package com.youtubeclone.app.data.repository

import com.youtubeclone.app.data.remote.VideoApi
import com.youtubeclone.app.data.remote.dto.toDomain
import com.youtubeclone.app.domain.model.DataError
import com.youtubeclone.app.domain.model.LikeStatus
import com.youtubeclone.app.domain.model.VideoDetail
import com.youtubeclone.app.domain.model.VideoFormat
import com.youtubeclone.app.domain.model.VideoPage
import com.youtubeclone.app.domain.repository.VideoRepository
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

class VideoRepositoryImpl @Inject constructor(
    private val api: VideoApi,
) : VideoRepository {

    override suspend fun getVideos(
        format: VideoFormat?,
        categoryId: String?,
        page: Int,
        pageSize: Int,
    ): VideoPage = call {
        api.getVideos(format?.name, categoryId, page, pageSize).toDomain()
    }

    override suspend fun getVideoDetail(id: String): VideoDetail = call {
        api.getVideo(id).toDomain()
    }

    override suspend fun recordView(id: String) = call {
        api.recordView(id)
    }

    override suspend fun setLiked(id: String, like: Boolean): LikeStatus = call {
        (if (like) api.like(id) else api.unlike(id)).toDomain()
    }

    override suspend fun getSubscriptionFeed(page: Int, pageSize: Int): VideoPage = call {
        api.getSubscriptionFeed(page, pageSize).toDomain()
    }

    override suspend fun getWatchHistory(page: Int, pageSize: Int): VideoPage = call {
        api.getWatchHistory(page, pageSize).toDomain()
    }

    override suspend fun getLikedVideos(page: Int, pageSize: Int): VideoPage = call {
        api.getLikedVideos(page, pageSize).toDomain()
    }

    // Traduit les exceptions réseau en erreurs du domaine, pour que les couches au-dessus
    // ne dépendent pas de Retrofit.
    private suspend fun <T> call(block: suspend () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            throw if (e.code() == 404) DataError.NotFound else DataError.Server(e.code())
        } catch (e: IOException) {
            throw DataError.Network
        }
}
