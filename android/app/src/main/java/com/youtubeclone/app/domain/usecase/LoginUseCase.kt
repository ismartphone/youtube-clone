package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.repository.AuthRepository
import javax.inject.Inject

/** Ouvre une session avec un email et un mot de passe. */
class LoginUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(email: String, password: String) = repository.login(email, password)
}
