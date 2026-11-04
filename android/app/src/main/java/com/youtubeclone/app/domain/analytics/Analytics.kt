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

/**
 * Test A/B du paywall (ADR-006) : variante attribuée par le drapeau PostHog « paywall-title ».
 * Ne bloque jamais : drapeau absent, pas encore chargé ou PostHog injoignable, la variante
 * par défaut [PaywallVariant.BENEFITS] s'affiche.
 */
interface Experiments {
    fun paywallVariant(): PaywallVariant
}

/** Variantes du titre du paywall. [key] : nom de la variante dans PostHog. */
enum class PaywallVariant(val key: String) {
    /** Titre orienté avantages, « Passez à Premium ». Variante par défaut. */
    BENEFITS("benefits"),

    /** Titre orienté prix, « Premium pour 4,99 € par mois ». */
    PRICE("price"),
    ;

    companion object {
        const val FLAG_KEY = "paywall-title"

        fun fromKey(key: String?): PaywallVariant = entries.firstOrNull { it.key == key } ?: BENEFITS
    }
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

    // Parcours du paywall : affiché, achat lancé, achat confirmé ou abandon. Chacun porte la
    // variante affichée, pour comparer les taux de conversion du test A/B.
    class PaywallViewed(variant: PaywallVariant) : AnalyticsEvent("paywall_viewed", variant.toProperties())
    class PurchaseStarted(variant: PaywallVariant) : AnalyticsEvent("purchase_started", variant.toProperties())
    class PurchaseConfirmed(variant: PaywallVariant) : AnalyticsEvent("purchase_confirmed", variant.toProperties())
    class PaywallDismissed(variant: PaywallVariant) : AnalyticsEvent("paywall_dismissed", variant.toProperties())

    // Publicités (ADR-021) : chaque événement porte l'identifiant de la publicité. Le lien
    // « Supprimer les publicités » a un nom par emplacement, pour comparer leur conversion.
    class AdStarted(adId: String) : AnalyticsEvent("ad_started", mapOf("ad_id" to adId))
    class AdSkipped(adId: String, elapsedSeconds: Int) :
        AnalyticsEvent("ad_skipped", mapOf("ad_id" to adId, "elapsed_seconds" to elapsedSeconds))
    class AdCompleted(adId: String) : AnalyticsEvent("ad_completed", mapOf("ad_id" to adId))
    class RemoveAdsClickedPreroll(adId: String) :
        AnalyticsEvent("remove_ads_clicked_preroll", mapOf("ad_id" to adId))
}

private fun PaywallVariant.toProperties(): Map<String, Any> = mapOf("paywall_variant" to key)
