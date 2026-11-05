package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.model.LIVE_CHAT_MAX_LENGTH
import com.youtubeclone.app.domain.model.LIVE_TITLE_MAX_LENGTH
import com.youtubeclone.app.domain.repository.AuthRepository
import com.youtubeclone.app.domain.repository.LivesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Cas d'usage des lives (ADR-029), réunis pour l'écran du live, le fil, la chaîne et la feuille de création. */
class LiveUseCases @Inject constructor(
    val repository: LivesRepository,
    private val auth: AuthRepository,
) {
    /** Identifiant du compte actif, pour savoir s'il est le diffuseur ; null pour un visiteur. */
    suspend fun currentUserId(): String? = auth.currentUserId.first()

    suspend fun start(title: String, videoId: String) =
        repository.startLive(title.trim().take(LIVE_TITLE_MAX_LENGTH), videoId)

    suspend fun send(liveId: String, content: String) =
        repository.sendMessage(liveId, content.trim().take(LIVE_CHAT_MAX_LENGTH))
}
