package com.youtubeclone.app.presentation.channel

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.youtubeclone.app.R

/** S'abonner (bouton clair, comme sur YouTube) ou Abonné (bouton discret). */
@Composable
fun FollowButton(
    state: FollowUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isFollowing) {
        FilledTonalButton(onClick = onClick, enabled = !state.isPending, modifier = modifier) {
            Text(stringResource(R.string.channel_following))
        }
    } else {
        Button(
            onClick = onClick,
            enabled = !state.isPending,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onBackground,
                contentColor = MaterialTheme.colorScheme.background,
            ),
            modifier = modifier,
        ) {
            Text(stringResource(R.string.channel_follow))
        }
    }
}
