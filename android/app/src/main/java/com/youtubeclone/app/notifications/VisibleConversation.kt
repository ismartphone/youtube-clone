package com.youtubeclone.app.notifications

/**
 * Conversation affichée à l'écran, s'il y en a une : sa notification de message ne s'affiche pas,
 * le message y arrive déjà en direct (ADR-025, décision 8). Le serveur, lui, envoie toujours.
 */
object VisibleConversation {
    @Volatile
    var id: String? = null
}
