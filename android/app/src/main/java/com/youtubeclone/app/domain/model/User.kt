package com.youtubeclone.app.domain.model

/** Profil de l'utilisateur connecté. */
data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
)
