package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.model.VideoAd
import com.youtubeclone.app.domain.repository.VideoRepository
import javax.inject.Inject

/** Bandeau sponsorisé en haut du fil (ADR-021) : null pour un compte Premium ou sans publicité. */
class GetSponsoredAdUseCase @Inject constructor(
    private val repository: VideoRepository,
) {
    suspend operator fun invoke(): VideoAd? = repository.getSponsoredAd()
}
