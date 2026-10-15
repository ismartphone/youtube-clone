package com.youtubeclone.app.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import coil3.compose.AsyncImage
import com.youtubeclone.app.domain.model.Channel

/** Avatar de la chaîne, ou à défaut un cercle avec l'initiale de son nom. La taille vient de [modifier]. */
@Composable
fun ChannelAvatar(
    channel: Channel,
    modifier: Modifier = Modifier,
    initialStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    if (channel.avatarUrl != null) {
        AsyncImage(
            model = channel.avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(CircleShape),
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = channel.name.take(1).uppercase(),
                style = initialStyle,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
