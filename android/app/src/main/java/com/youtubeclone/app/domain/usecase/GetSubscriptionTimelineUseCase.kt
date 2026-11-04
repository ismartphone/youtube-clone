package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.model.Post
import com.youtubeclone.app.domain.model.SubscriptionFeedItem
import com.youtubeclone.app.domain.model.Video
import com.youtubeclone.app.domain.model.VideoPage
import com.youtubeclone.app.domain.repository.PostRepository
import com.youtubeclone.app.domain.repository.VideoRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Fil Abonnements : vidéos et posts des chaînes suivies, mêlés et classés par date, du plus récent
 * au plus ancien. Première page des vidéos seulement, comme le fil d'accueil. Exige une session.
 * Les posts sont un complément : s'ils ne se chargent pas, le fil garde ses vidéos.
 */
class GetSubscriptionTimelineUseCase @Inject constructor(
    private val videos: VideoRepository,
    private val posts: PostRepository,
) {
    suspend operator fun invoke(): List<SubscriptionFeedItem> = coroutineScope {
        val postPage = async {
            try {
                posts.getFollowedPosts(page = 1, pageSize = POSTS_PAGE_SIZE).items
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
        }
        val videoPage = videos.getSubscriptionFeed(page = 1, pageSize = GetVideosUseCase.DEFAULT_PAGE_SIZE)
        mergeByDate(videoPage, postPage.await())
    }

    companion object {
        const val POSTS_PAGE_SIZE = 20

        /**
         * Mêle [videoPage] et [posts] par date. Si d'autres vidéos existent au-delà de la page, un
         * post plus ancien que la dernière vidéo chargée est écarté : des vidéos plus récentes que
         * lui manqueraient avant lui.
         */
        fun mergeByDate(videoPage: VideoPage, posts: List<Post>): List<SubscriptionFeedItem> {
            val videos: List<Video> = videoPage.items
            val complete = videoPage.page * videoPage.pageSize >= videoPage.total
            val oldestVideo = videos.minOfOrNull { it.publishedAt }
            val keptPosts = if (complete || oldestVideo == null) posts else posts.filter { it.createdAt >= oldestVideo }
            return (videos.map { SubscriptionFeedItem.OfVideo(it) } + keptPosts.map { SubscriptionFeedItem.OfPost(it) })
                .sortedByDescending { it.date }
        }
    }
}
