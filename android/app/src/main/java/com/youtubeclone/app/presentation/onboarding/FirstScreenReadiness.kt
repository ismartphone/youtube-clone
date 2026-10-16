package com.youtubeclone.app.presentation.onboarding

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Signale que le premier écran affiché après l'animation d'ouverture a fini son premier chargement
 * (fil d'accueil, ou catégories au premier lancement), avec succès ou en erreur.
 * L'animation attend ce signal avant de s'effacer, pour ne pas révéler un écran encore vide.
 */
@Singleton
class FirstScreenReadiness @Inject constructor() {
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    fun markReady() {
        _isReady.value = true
    }
}
