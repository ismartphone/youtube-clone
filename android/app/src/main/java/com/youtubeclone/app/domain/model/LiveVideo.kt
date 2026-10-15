package com.youtubeclone.app.domain.model

/** « J'aime » d'une vidéo, vus par l'utilisateur courant. [isLiked] vaut toujours false pour un visiteur. */
data class LikeStatus(
    val likeCount: Int,
    val isLiked: Boolean,
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
