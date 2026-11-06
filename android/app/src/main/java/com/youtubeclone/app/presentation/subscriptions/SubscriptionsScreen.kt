package com.youtubeclone.app.presentation.subscriptions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.youtubeclone.app.R
import com.youtubeclone.app.presentation.theme.redButtonColors
import com.youtubeclone.app.domain.model.SubscriptionFeedItem
import com.youtubeclone.app.presentation.channel.PostCard
import com.youtubeclone.app.presentation.common.ErrorState
import com.youtubeclone.app.presentation.common.LoadingState
import com.youtubeclone.app.presentation.common.MessageState
import com.youtubeclone.app.presentation.home.VideoCard
import com.youtubeclone.app.presentation.home.VideoCardSpacing

/** Onglet Abonnements : vidéos des chaînes suivies (GET /me/feed). */
@Composable
fun SubscriptionsScreen(
    onVideoClick: (videoId: String) -> Unit,
    onChannelClick: (channelId: String) -> Unit,
    onPostClick: (channelId: String) -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SubscriptionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        SubscriptionsUiState.Loading -> LoadingState(modifier)
        SubscriptionsUiState.LoggedOut -> LoggedOutContent(onLoginClick, modifier)
        SubscriptionsUiState.Empty -> MessageState(stringResource(R.string.subscriptions_empty), modifier)
        is SubscriptionsUiState.Error -> ErrorState(state.error, viewModel::load, modifier)
        is SubscriptionsUiState.Success -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(VideoCardSpacing),
        ) {
            items(state.items, key = { it.id }) { item ->
                when (item) {
                    is SubscriptionFeedItem.OfVideo -> VideoCard(
                        video = item.video,
                        onChannelClick = { onChannelClick(item.video.channel.id) },
                        modifier = Modifier.clickable(role = Role.Button) { onVideoClick(item.video.id) },
                    )
                    is SubscriptionFeedItem.OfPost -> PostCard(item.post, onClick = { onPostClick(item.post.channel.id) })
                }
            }
        }
    }
}

@Composable
private fun LoggedOutContent(onLoginClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(R.string.subscriptions_logged_out),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onLoginClick, colors = redButtonColors()) { Text(stringResource(R.string.login_submit)) }
    }
}
