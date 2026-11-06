package com.youtubeclone.app.data.remote.dto

import kotlinx.serialization.Serializable

// Réponse de POST /live/participant-token (ADR-031) : JWT à transmettre tel quel au SDK.
@Serializable
data class ParticipantTokenDto(val token: String)
