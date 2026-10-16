package com.youtubeclone.app.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.youtubeclone.app.R
import com.youtubeclone.app.presentation.common.ErrorState
import com.youtubeclone.app.presentation.common.MessageState
import com.youtubeclone.app.presentation.common.VideoListSkeleton
import com.youtubeclone.app.presentation.common.rememberStaggeredAppearance
import com.youtubeclone.app.presentation.common.staggeredAppearance

@Composable
fun HomeScreen(
    onVideoClick: (videoId: String) -> Unit,
    onChannelClick: (channelId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        uiState = uiState,
        onRetry = viewModel::load,
        onVideoClick = onVideoClick,
        onChannelClick = onChannelClick,
        modifier = modifier,
    )
}

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    onRetry: () -> Unit,
    onVideoClick: (videoId: String) -> Unit,
    onChannelClick: (channelId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        HomeUiState.Loading -> VideoListSkeleton(modifier)
        HomeUiState.Empty -> MessageState(stringResource(R.string.home_empty), modifier)
        is HomeUiState.Error -> ErrorState(uiState.error, onRetry, modifier)
        is HomeUiState.Success -> {
            // Nouvelle liste chargée : ses premières cartes apparaissent l'une après l'autre.
            val appearance = rememberStaggeredAppearance(uiState)
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(uiState.videos, key = { _, video -> video.id }) { index, video ->
                    VideoCard(
                        video = video,
                        onChannelClick = { onChannelClick(video.channel.id) },
                        modifier = Modifier
                            .staggeredAppearance(appearance, video.id, index)
                            .clickable { onVideoClick(video.id) },
                    )
                }
            }
        }
    }
}
