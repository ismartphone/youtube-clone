package com.youtubeclone.app.presentation.theme

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme

// Couleurs de boutons qui portent du texte (ADR-030, D1).

/** Bouton plein rouge : fond [YoutubeRedFill] pour que le texte blanc atteigne 4,5:1. */
@Composable
fun redButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = YoutubeRedFill,
    contentColor = MaterialTheme.colorScheme.onPrimary,
)

/** Bouton texte rouge : [YoutubeRedText] reste lisible sur les feuilles et boîtes de dialogue grises. */
@Composable
fun redTextButtonColors(): ButtonColors = ButtonDefaults.textButtonColors(contentColor = YoutubeRedText)
