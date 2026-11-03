package com.youtubeclone.app.domain.model

/** Chaîne trouvée par la recherche, avec son nombre d'abonnés. */
data class ChannelSearchResult(
    val channel: Channel,
    val followerCount: Int,
)

/**
 * Une page de résultats de recherche. Vidéos et chaînes sont paginées ensemble : la page N
 * contient la page N de chacune.
 */
data class SearchPage(
    val videos: VideoPage,
    val channels: List<ChannelSearchResult>,
    val channelTotal: Int,
)
