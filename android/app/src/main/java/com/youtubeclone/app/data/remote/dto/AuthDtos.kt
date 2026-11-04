package com.youtubeclone.app.data.remote.dto

import com.youtubeclone.app.data.remote.resolveMediaUrl
import com.youtubeclone.app.domain.model.User
import kotlinx.serialization.Serializable

// Corps et réponses du module auth du backend (auth.bodies.ts et auth.presenter.ts).

@Serializable
data class RegisterRequestDto(
    val email: String,
    val password: String,
    val displayName: String,
)

@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String,
)

@Serializable
data class RefreshRequestDto(
    val refreshToken: String,
)

@Serializable
data class TokenPairDto(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int,
)

@Serializable
data class UserDto(
    val id: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String? = null,
)

fun UserDto.toDomain() = User(
    id = id,
    email = email,
    displayName = displayName,
    avatarUrl = avatarUrl?.let(::resolveMediaUrl),
)
