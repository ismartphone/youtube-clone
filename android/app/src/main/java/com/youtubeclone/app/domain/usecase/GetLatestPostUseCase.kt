package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.model.Post
import com.youtubeclone.app.domain.repository.PostRepository
import javax.inject.Inject

/** Post mis en avant dans le fil d'accueil : le plus récent de la plateforme, ou null. */
class GetLatestPostUseCase @Inject constructor(
    private val repository: PostRepository,
) {
    suspend operator fun invoke(): Post? = repository.getLatestPost()
}
