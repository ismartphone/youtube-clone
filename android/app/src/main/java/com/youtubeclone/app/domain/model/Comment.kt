package com.youtubeclone.app.domain.model

import java.time.Instant

data class CommentAuthor(
    val id: String,
    val displayName: String,
    val avatarUrl: String?,
    /** @handle de sa chaîne ; null sans chaîne (ADR-023). */
    val handle: String? = null,
)

/** Avis de l'utilisateur sur un commentaire (ADR-023). Seuls les « j'aime » sont comptés. */
enum class CommentReaction { Like, Dislike }

data class Comment(
    val id: String,
    val videoId: String,
    val content: String,
    val createdAt: Instant,
    val author: CommentAuthor,
    /** Commentaire de premier niveau auquel celui-ci répond ; null pour un commentaire. */
    val parentId: String? = null,
    val likeCount: Int = 0,
    /** Nombre de réponses ; toujours 0 pour une réponse (un seul niveau). */
    val replyCount: Int = 0,
    /** Cœur du propriétaire de la chaîne. */
    val heartedByCreator: Boolean = false,
    /** Avis de l'utilisateur ; null pour un visiteur ou sans avis. */
    val reaction: CommentReaction? = null,
)

/**
 * Une page de commentaires ou de réponses. Pour les commentaires de la vidéo, [total] compte
 * aussi les réponses : c'est le nombre affiché sous la vidéo.
 */
data class CommentPage(
    val items: List<Comment>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
)

/** « Top commentaires » (par défaut) ou « Les plus récents » (ADR-023). */
enum class CommentSort { Top, Newest }

/** Résultat d'un avis : le nouveau nombre de « j'aime » et l'avis retenu. */
data class CommentReactionStatus(val likeCount: Int, val reaction: CommentReaction?)
