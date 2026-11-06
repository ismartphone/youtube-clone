package com.youtubeclone.app.data.remote

import com.youtubeclone.app.data.remote.dto.ParticipantTokenDto
import retrofit2.http.POST

// Live partagé (ADR-031) : notre backend signe le Jeton de Participant de l'utilisateur connecté.
// Distinct de LivesApi, qui sert notre live shopping (ADR-029).
interface SharedLiveApi {
    @POST("live/participant-token")
    suspend fun participantToken(): ParticipantTokenDto
}
