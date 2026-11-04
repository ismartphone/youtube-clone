package com.youtubeclone.app.domain.model

/**
 * Publicité vidéo (ADR-021), tirée au sort par le serveur : jamais pour un compte Premium ni
 * avant un Short. [streamUrl] : manifeste HLS quand il est prêt ([isHls]), MP4 sinon.
 * [destinationUrl] : site de l'annonceur, ouvert par « Visiter le site » et par le bandeau.
 */
data class VideoAd(
    val id: String,
    val title: String,
    val streamUrl: String,
    val isHls: Boolean,
    val thumbnailUrl: String?,
    val durationSeconds: Int,
    val destinationUrl: String? = null,
) {
    /** Nom affiché de l'annonceur : le domaine de son site, sans « www. ». */
    val advertiser: String?
        get() = destinationUrl?.let { runCatching { java.net.URI(it).host }.getOrNull() }?.removePrefix("www.")
}
