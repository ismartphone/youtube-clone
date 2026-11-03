package com.youtubeclone.app.domain.model

/** Contenu publié depuis l'application (ADR-015), annoncé aux écrans qui l'affichent. */
sealed interface Publication {
    val channelId: String

    data class VideoPublished(val video: Video) : Publication {
        override val channelId: String get() = video.channel.id
    }

    data class PostPublished(val post: Post) : Publication {
        override val channelId: String get() = post.channel.id
    }
}
