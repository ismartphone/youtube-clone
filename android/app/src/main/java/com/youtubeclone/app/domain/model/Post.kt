package com.youtubeclone.app.domain.model

import java.time.Instant

// Post d'une chaîne (ADR-015) : texte et image facultative. Mêmes bornes que le serveur.

/** Texte de 500 caractères au plus. */
const val POST_TEXT_MAX_LENGTH = 500

/** Image de 5 Mo au plus ; le serveur vérifie aussi qu'il s'agit d'un JPEG ou d'un PNG. */
const val MAX_POST_IMAGE_BYTES = 5L * 1024 * 1024

data class Post(
    val id: String,
    val text: String,
    /** URL complète de l'image, null sans image. */
    val imageUrl: String?,
    val createdAt: Instant,
    val channel: Channel,
)

data class PostPage(
    val items: List<Post>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
)

/** Raison pour laquelle une image choisie ne peut pas accompagner un post. */
enum class PostImageProblem { Unreadable, TooLarge }
