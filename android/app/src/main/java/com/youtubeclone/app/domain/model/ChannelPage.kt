package com.youtubeclone.app.domain.model

/**
 * Cloche d'un abonnement (ADR-020) : [All], toutes les publications ; [Personalized], les vidéos
 * classiques sans les Shorts (réglage par défaut) ; [None], rien.
 */
enum class NotificationLevel { All, Personalized, None }

/**
 * Abonnés d'une chaîne (FOLLOW, abonnement gratuit), vus par l'utilisateur courant.
 * [isFollowing] vaut toujours false pour un visiteur ; [notificationLevel] est null sans abonnement.
 */
data class FollowStatus(
    val followerCount: Int,
    val isFollowing: Boolean,
    val notificationLevel: NotificationLevel? = null,
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

/**
 * Chaîne telle que son propriétaire la personnalise (ADR-018). [hasPhoto] : photo choisie par le
 * créateur, qui peut la retirer ; sinon l'avatar est généré à partir du nom.
 */
data class ChannelProfile(
    val channel: Channel,
    val bannerUrl: String?,
    val description: String?,
) {
    // Les photos envoyées sont rangées par le serveur sous /media/channel-photos/.
    val hasPhoto: Boolean get() = channel.avatarUrl?.contains("/media/channel-photos/") == true
}

/** Abonnement ou désabonnement réussi, diffusé à tous les écrans qui affichent cette chaîne. */
data class FollowChange(
    val channelId: String,
    val status: FollowStatus,
)
