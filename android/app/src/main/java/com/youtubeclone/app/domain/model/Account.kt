package com.youtubeclone.app.domain.model

/** Comptes enregistrés sur l'appareil au plus (ADR-022, décision 6). */
const val MAX_ACCOUNTS = 5

/** Compte enregistré sur l'appareil, tel que la feuille des comptes le montre (ADR-022). */
data class Account(
    val userId: String,
    val displayName: String,
    val email: String,
    /** @handle de sa chaîne ; null sans chaîne : la feuille montre alors l'email (décision 1). */
    val handle: String?,
    val avatarUrl: String?,
    val isActive: Boolean,
    /** Session expirée : grisé, « Reconnexion requise », la bascule passe par la connexion (décision 2). */
    val needsReconnection: Boolean,
)
