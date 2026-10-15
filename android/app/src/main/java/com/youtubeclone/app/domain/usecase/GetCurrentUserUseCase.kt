package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.model.User
import com.youtubeclone.app.domain.repository.AuthRepository
import javax.inject.Inject

class GetCurrentUserUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(): User = repository.getCurrentUser()
}
