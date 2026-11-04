package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.repository.AuthRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Identifiant du compte actif, puis chaque changement : connexion, bascule, déconnexion (ADR-022). */
class ObserveActiveAccountUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    operator fun invoke(): Flow<String?> = repository.currentUserId
}
