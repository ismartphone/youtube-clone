package com.youtubeclone.app.domain.model

/**
 * « J'aime » d'une vidéo, vus par l'utilisateur courant. [isLiked] et [isDisliked] valent toujours
 * false pour un visiteur. Le nombre de « Je n'aime pas » n'existe pas : il n'est jamais affiché.
 */
data class LikeStatus(
    val likeCount: Int,
    val isLiked: Boolean,
    val isDisliked: Boolean = false,
)

/** Événements reçus en direct pour la vidéo regardée (ADR-004). */
sealed interface LiveVideoEvent {
    /**
     * La connexion est (r)établie. Ce qui a été diffusé pendant une coupure est perdu :
     * après une reconnexion, l'écran relit les compteurs et les commentaires.
     */
    data object Connected : LiveVideoEvent

    data class CommentAdded(val comment: Comment) : LiveVideoEvent
    data class ViewsUpdated(val viewCount: Int) : LiveVideoEvent
    data class LikesUpdated(val likeCount: Int) : LiveVideoEvent
}
