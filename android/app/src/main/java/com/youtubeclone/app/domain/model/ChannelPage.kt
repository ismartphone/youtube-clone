package com.youtubeclone.app.domain.model

/**
 * Abonnés d'une chaîne (FOLLOW, abonnement gratuit), vus par l'utilisateur courant.
 * [isFollowing] vaut toujours false pour un visiteur.
 */
data class FollowStatus(
    val followerCount: Int,
    val isFollowing: Boolean,
)

/**
 * Page d'une chaîne : sa présentation, ses abonnés et ses vidéos, de la plus récente à la plus ancienne.
 * [isOwner] vaut true quand l'utilisateur connecté en est le propriétaire : il ne peut pas s'y abonner.
 * [bannerUrl] : bannière en haut de la page, null si la chaîne n'en a pas.
 */
data class ChannelPage(
    val channel: Channel,
    val bannerUrl: String?,
    val description: String?,
    val followStatus: FollowStatus,
    val isOwner: Boolean,
    val videos: List<Video>,
)

/** Abonnement ou désabonnement réussi, diffusé à tous les écrans qui affichent cette chaîne. */
data class FollowChange(
    val channelId: String,
    val status: FollowStatus,
)
