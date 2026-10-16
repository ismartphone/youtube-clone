package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.model.Video
import com.youtubeclone.app.domain.model.VideoFormat
import com.youtubeclone.app.domain.repository.PreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Fil d'accueil : vidéos classiques (les Shorts ont leur onglet), de la plus récente à la plus ancienne.
 * Sans filtre, les vidéos des centres d'intérêt choisis passent en premier ; avec un filtre de
 * catégorie, seules les vidéos de cette catégorie sont affichées.
 */
class GetHomeFeedUseCase @Inject constructor(
    private val getVideos: GetVideosUseCase,
    private val preferences: PreferencesRepository,
) {
    suspend operator fun invoke(categoryId: String? = null): List<Video> {
        val videos = getVideos(format = VideoFormat.LONG, categoryId = categoryId).items
        if (categoryId != null) return videos
        return videos.withInterestsFirst(preferences.interests.first())
    }
}

/**
 * Place d'abord les vidéos des catégories [interests], sans changer l'ordre par date à l'intérieur
 * de chaque groupe. Sans centre d'intérêt, la liste est inchangée.
 * Le tri porte sur la page chargée (les 20 vidéos les plus récentes), comme le fil d'accueil.
 */
fun List<Video>.withInterestsFirst(interests: Set<String>): List<Video> {
    if (interests.isEmpty()) return this
    val (preferred, others) = partition { it.category?.id in interests }
    return preferred + others
}
