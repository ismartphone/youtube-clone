package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.model.Video
import com.youtubeclone.app.domain.model.VideoFormat
import javax.inject.Inject

/**
 * Vidéos suggérées sous le lecteur. Il n'y a pas encore de recommandation côté serveur :
 * ce sont les vidéos classiques récentes, sans celle en cours, celles de la même catégorie d'abord.
 */
class GetSuggestedVideosUseCase @Inject constructor(
    private val getVideos: GetVideosUseCase,
) {
    suspend operator fun invoke(current: Video): List<Video> {
        val others = getVideos(format = VideoFormat.LONG).items.filter { it.id != current.id }
        val categoryId = current.category?.id ?: return others
        val (sameCategory, rest) = others.partition { it.category?.id == categoryId }
        return sameCategory + rest
    }
}
