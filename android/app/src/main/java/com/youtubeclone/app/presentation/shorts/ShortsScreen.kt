package com.youtubeclone.app.presentation.shorts

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.youtubeclone.app.R
import com.youtubeclone.app.presentation.common.PlaceholderScreen

// Écran provisoire : le défilement vertical des Shorts viendra plus tard.
@Composable
fun ShortsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(title = stringResource(R.string.tab_shorts), modifier = modifier)
}
