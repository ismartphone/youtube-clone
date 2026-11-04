package com.youtubeclone.app.domain.model

import java.time.Instant

/** Élément du fil Abonnements : une vidéo ou un post des chaînes suivies, mêlés par date. */
sealed interface SubscriptionFeedItem {
    val id: String
    val date: Instant

    data class OfVideo(val video: Video) : SubscriptionFeedItem {
        override val id get() = "video-${video.id}"
        override val date: Instant get() = video.publishedAt
    }

    data class OfPost(val post: Post) : SubscriptionFeedItem {
        override val id get() = "post-${post.id}"
        override val date: Instant get() = post.createdAt
    }
}
