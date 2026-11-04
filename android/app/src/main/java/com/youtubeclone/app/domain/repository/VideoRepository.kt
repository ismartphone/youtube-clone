package com.youtubeclone.app.domain.repository

import com.youtubeclone.app.domain.model.LikeStatus
import com.youtubeclone.app.domain.model.VideoDetail
import com.youtubeclone.app.domain.model.VideoAd
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

    /** Publicité du bandeau sponsorisé (ADR-021) ; null pour un compte Premium. */
    suspend fun getSponsoredAd(): VideoAd?

    suspend fun recordView(id: String)

    /** Vidéos des chaînes suivies par l'utilisateur connecté. */
    suspend fun getSubscriptionFeed(page: Int, pageSize: Int): VideoPage

    /** Historique de l'utilisateur connecté, de la vidéo vue le plus récemment à la plus ancienne. */
    suspend fun getWatchHistory(page: Int, pageSize: Int): VideoPage

    /** Vidéos aimées par l'utilisateur connecté, de la plus récemment aimée à la plus ancienne. */
    suspend fun getLikedVideos(page: Int, pageSize: Int): VideoPage

    /** Aime ou retire son « j'aime ». Exige une session ouverte. */
    suspend fun setLiked(id: String, like: Boolean): LikeStatus

    /** Donne (true) ou retire (false) un « Je n'aime pas » ; il remplace le « j'aime » éventuel. */
    suspend fun setDisliked(id: String, dislike: Boolean): LikeStatus
}
