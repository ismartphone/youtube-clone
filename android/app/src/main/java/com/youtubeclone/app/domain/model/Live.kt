package com.youtubeclone.app.domain.model

// Diffusion en direct (ADR-015) : seul le seuil de déblocage est développé, calculé sur les
// abonnés réels de la chaîne. La diffusion elle-même est hors périmètre.

/** Abonnés nécessaires pour débloquer le direct, comme sur YouTube. */
const val LIVE_MIN_FOLLOWERS = 50
