package com.youtubeclone.app.presentation.profile

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.youtubeclone.app.R
import com.youtubeclone.app.presentation.theme.YoutubeCloneTheme

/**
 * Listes personnelles ouvertes depuis l'onglet « Vous ».
 * Le backend n'a pas encore de route pour l'historique ni pour les vidéos aimées : ces écrans
 * affichent un état vide, en attendant d'être branchés sur de vraies listes.
 */
enum class YouList(
    @StringRes val title: Int,
    @DrawableRes val icon: Int,
    @StringRes val emptyTitle: Int,
    @StringRes val emptyMessage: Int,
) {
    History(R.string.you_history, R.drawable.ic_history, R.string.you_history_empty_title, R.string.you_history_empty_message),
    Liked(R.string.you_liked_videos, R.drawable.ic_like, R.string.you_liked_empty_title, R.string.you_liked_empty_message),
    // Seulement sans chaîne : avec une chaîne, Vos vidéos ouvre directement sa page.
    YourVideos(R.string.you_your_videos, R.drawable.ic_your_videos, R.string.you_videos_empty_title, R.string.you_videos_empty_message),
}

@Composable
fun YouListScreen(list: YouList, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(list.title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            EmptyStateMessage(
                icon = list.icon,
                title = stringResource(list.emptyTitle),
                message = stringResource(list.emptyMessage),
            )
        }
    }
}

/** État vide : icône grise, titre, puis message en gris. */
@Composable
internal fun EmptyStateMessage(
    @DrawableRes icon: Int,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(bottom = 4.dp)
                .size(40.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F0F0F, heightDp = 500)
@Composable
private fun YouListScreenPreview() {
    YoutubeCloneTheme {
        YouListScreen(YouList.History)
    }
}
