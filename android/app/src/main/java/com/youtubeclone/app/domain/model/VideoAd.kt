package com.youtubeclone.app.domain.model

/**
 * Publicité vidéo (ADR-021), tirée au sort par le serveur : jamais pour un compte Premium ni
 * avant un Short. [streamUrl] : manifeste HLS quand il est prêt ([isHls]), MP4 sinon.
 * [destinationUrl] : site de l'annonceur, ouvert par le bouton principal du bandeau et par la
 * bande pendant l'annonce, qui portent le libellé [ctaLabel]. [advertiserName], [description] et
 * [logoUrl] : carte du bandeau ; sans logo, l'initiale de l'annonceur.
 */
data class VideoAd(
    val id: String,
    val title: String,
    val streamUrl: String,
    val isHls: Boolean,
    val thumbnailUrl: String?,
    val durationSeconds: Int,
    val destinationUrl: String? = null,
    val advertiserName: String? = null,
    val description: String? = null,
    val logoUrl: String? = null,
    val ctaLabel: String = DEFAULT_AD_CTA,
) {
    /** Nom affiché de l'annonceur ; à défaut (publicité antérieure à ce champ), le domaine du site. */
    val advertiser: String?
        get() = advertiserName
            ?: destinationUrl?.let { runCatching { java.net.URI(it).host }.getOrNull() }?.removePrefix("www.")
}

/** Libellé du bouton principal quand la publicité n'en précise pas. */
const val DEFAULT_AD_CTA = "Visiter le site"
