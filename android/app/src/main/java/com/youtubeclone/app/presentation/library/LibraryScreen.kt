package com.youtubeclone.app.presentation.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.youtubeclone.app.R
import com.youtubeclone.app.presentation.common.PlaceholderScreen

// Écran provisoire.
@Composable
fun LibraryScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(title = stringResource(R.string.tab_library), modifier = modifier)
}
