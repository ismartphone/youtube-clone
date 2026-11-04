package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.model.ChannelImageKind
import com.youtubeclone.app.domain.model.ChannelProfile
import com.youtubeclone.app.domain.model.MAX_CHANNEL_IMAGE_BYTES
import com.youtubeclone.app.domain.repository.ChannelRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

// Personnalisation de sa chaîne par le créateur (ADR-018).

/** Nom, handle et description ; un paramètre null n'est pas modifié. */
class UpdateOwnChannelUseCase @Inject constructor(
    private val repository: ChannelRepository,
) {
    suspend operator fun invoke(name: String?, handle: String?, description: String?): ChannelProfile =
        repository.updateOwnChannel(name?.trim(), handle, description?.trim())
}

/** Image refusée avant l'envoi : illisible, ou plus lourde que la limite du serveur. */
enum class ChannelImageProblem { Unreadable, TooLarge }

class SetOwnChannelImageUseCase @Inject constructor(
    private val repository: ChannelRepository,
) {
    /** Vérifie la taille avant l'envoi ; le serveur revérifie et analyse l'image. */
    suspend fun check(uri: String): ChannelImageProblem? {
        val size = repository.imageSize(uri) ?: return ChannelImageProblem.Unreadable
        return if (size > MAX_CHANNEL_IMAGE_BYTES) ChannelImageProblem.TooLarge else null
    }

    suspend operator fun invoke(kind: ChannelImageKind, uri: String): ChannelProfile =
        repository.setOwnChannelImage(kind, uri)
}

class RemoveOwnChannelImageUseCase @Inject constructor(
    private val repository: ChannelRepository,
) {
    suspend operator fun invoke(kind: ChannelImageKind): ChannelProfile = repository.removeOwnChannelImage(kind)
}

class ObserveOwnChannelUpdatedUseCase @Inject constructor(
    private val repository: ChannelRepository,
) {
    operator fun invoke(): Flow<ChannelProfile> = repository.ownChannelUpdated
}
