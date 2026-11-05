package com.youtubeclone.app.presentation.common

import androidx.compose.runtime.compositionLocalOf

/**
 * Un lecteur est ouvert (écran de lecture ou mini-lecteur, ADR-024). Fourni une fois par la
 * navigation. Le bandeau sponsorisé du fil se suspend tant que c'est vrai : deux vidéos en même
 * temps, c'est deux décodeurs.
 */
val LocalNowPlayingActive = compositionLocalOf { false }
