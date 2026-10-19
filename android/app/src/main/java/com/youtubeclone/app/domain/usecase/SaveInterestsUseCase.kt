package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.repository.PreferencesRepository
import javax.inject.Inject

/** Enregistre les centres d'intérêt, choisis depuis l'onglet « Vous ». */
class SaveInterestsUseCase @Inject constructor(
    private val repository: PreferencesRepository,
) {
    suspend operator fun invoke(interests: Set<String>) = repository.setInterests(interests)
}
