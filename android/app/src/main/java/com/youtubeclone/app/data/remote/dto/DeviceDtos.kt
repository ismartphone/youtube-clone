package com.youtubeclone.app.data.remote.dto

import kotlinx.serialization.Serializable

/** Corps de POST /me/devices (ADR-020). */
@Serializable
data class DeviceTokenDto(
    val token: String,
)
