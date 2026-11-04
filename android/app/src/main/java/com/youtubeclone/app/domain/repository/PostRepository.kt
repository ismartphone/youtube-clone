package com.youtubeclone.app.domain.repository

import com.youtubeclone.app.domain.model.Post
import com.youtubeclone.app.domain.model.PostPage

/** Posts des chaînes (ADR-015). Les implémentations lèvent une [com.youtubeclone.app.domain.model.DataError] en cas d'échec. */
interface PostRepository {
    /** Taille en octets d'une image choisie dans la galerie ; null si elle est illisible. */
    suspend fun imageSize(uri: String): Long?

    /** Publie un post sur la chaîne de l'utilisateur connecté, avec une image facultative. */
    suspend fun createPost(text: String, imageUri: String?): Post

    /** Posts d'une chaîne, du plus récent au plus ancien. */
    suspend fun getChannelPosts(channelId: String, page: Int, pageSize: Int): PostPage

    /** Post le plus récent de la plateforme, ou null s'il n'y en a aucun. */
    suspend fun getLatestPost(): Post?

    /** Posts des chaînes suivies, du plus récent au plus ancien. Exige une session ouverte. */
    suspend fun getFollowedPosts(page: Int, pageSize: Int): PostPage
}
