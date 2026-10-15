package com.youtubeclone.app.presentation.channel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.ChannelPage
import com.youtubeclone.app.presentation.common.ChannelAvatar
import com.youtubeclone.app.presentation.common.ErrorToast
import com.youtubeclone.app.presentation.common.ErrorState
import com.youtubeclone.app.presentation.common.LoadingState
import com.youtubeclone.app.presentation.common.formatFollowerCount
import com.youtubeclone.app.presentation.home.VideoCard

@Composable
fun ChannelScreen(
    onVideoClick: (videoId: String) -> Unit,
    onLoginRequired: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChannelViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val followState by viewModel.follow.state.collectAsStateWithLifecycle()
    ErrorToast(viewModel.follow.errors)

    when (val state = uiState) {
        ChannelUiState.Loading -> LoadingState(modifier)
        is ChannelUiState.Error -> ErrorState(state.error, viewModel::load, modifier)
        is ChannelUiState.Success -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ChannelHeader(
                    page = state.page,
                    followState = followState,
                    // Visiteur : la connexion est demandée au moment de s'abonner.
                    onFollowClick = { if (isLoggedIn) viewModel.follow.toggle() else onLoginRequired() },
                )
            }
            if (state.page.videos.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.channel_no_videos),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                    )
                }
            }
            items(state.page.videos, key = { it.id }) { video ->
                VideoCard(video = video, modifier = Modifier.clickable { onVideoClick(video.id) })
            }
        }
    }
}

@Composable
private fun ChannelHeader(
    page: ChannelPage,
    followState: FollowUiState?,
    onFollowClick: () -> Unit,
) {
    val channel = page.channel
    // Le compteur suit le bouton : il change dès que l'abonnement est confirmé.
    val followerCount = followState?.followerCount ?: page.followStatus.followerCount
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ChannelAvatar(
                channel = channel,
                modifier = Modifier.size(72.dp),
                initialStyle = MaterialTheme.typography.headlineSmall,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "@${channel.handle} · ${formatFollowerCount(followerCount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        page.description?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (followState != null && followState.canFollow) {
            FollowButton(state = followState, onClick = onFollowClick, modifier = Modifier.fillMaxWidth())
        }
    }
}
