package com.youtubeclone.app.domain.analytics

import com.youtubeclone.app.domain.model.Video

/**
 * Mesures d'audience (ADR-006, ADR-013). Les événements partent de l'application vers PostHog,
 * jamais par le backend. Aucune donnée directement identifiante : ni email, ni nom, ni handle.
 * L'implémentation n'envoie rien quand l'utilisateur a coupé le partage des statistiques.
 */
interface Analytics {
    fun track(event: AnalyticsEvent)

    /** Écran affiché, par son nom de destination (sans identifiant de vidéo ni de chaîne). */
    fun screen(name: String)

    /** Associe les événements suivants au compte, par son seul identifiant interne (UUID). */
    fun identify(userId: String)

    /** Fin de session : les événements suivants redeviennent anonymes. */
    fun reset()
}

/** Événements mesurés. Les noms suivent la convention snake_case de PostHog. */
sealed class AnalyticsEvent(val name: String, val properties: Map<String, Any> = emptyMap()) {
    data object SignedUp : AnalyticsEvent("signed_up")
    data object LoggedIn : AnalyticsEvent("logged_in")
    data object LoggedOut : AnalyticsEvent("logged_out")

    class VideoPlayed(video: Video) : AnalyticsEvent(
        "video_played",
        buildMap {
            put("video_id", video.id)
            put("format", video.format.name)
            video.category?.let {
                put("category_id", it.id)
                put("category_name", it.name)
            }
        },
    )

    class VideoLiked(videoId: String) : AnalyticsEvent("video_liked", mapOf("video_id" to videoId))
    class CommentPosted(videoId: String) : AnalyticsEvent("comment_posted", mapOf("video_id" to videoId))
    class ChannelFollowed(channelId: String) : AnalyticsEvent("channel_followed", mapOf("channel_id" to channelId))

    // Parcours du paywall : affiché, achat lancé, achat confirmé ou abandon.
    data object PaywallViewed : AnalyticsEvent("paywall_viewed")
    data object PurchaseStarted : AnalyticsEvent("purchase_started")
    data object PurchaseConfirmed : AnalyticsEvent("purchase_confirmed")
    data object PaywallDismissed : AnalyticsEvent("paywall_dismissed")
}
