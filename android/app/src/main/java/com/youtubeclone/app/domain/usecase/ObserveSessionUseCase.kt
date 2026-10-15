package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.repository.AuthRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Indique en continu si une session est ouverte. */
class ObserveSessionUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.isLoggedIn
}
