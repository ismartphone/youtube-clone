package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.model.Channel
import com.youtubeclone.app.domain.repository.ChannelRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Crée la chaîne de l'utilisateur connecté (une seule par compte). */
class CreateChannelUseCase @Inject constructor(
    private val repository: ChannelRepository,
) {
    suspend operator fun invoke(name: String, handle: String, description: String?): Channel =
        repository.createChannel(name.trim(), handle, description?.trim()?.ifEmpty { null })
}

class ObserveOwnChannelCreatedUseCase @Inject constructor(
    private val repository: ChannelRepository,
) {
    operator fun invoke(): Flow<Channel> = repository.ownChannelCreated
}
