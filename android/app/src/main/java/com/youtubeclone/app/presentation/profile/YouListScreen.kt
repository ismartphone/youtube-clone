package com.youtubeclone.app.presentation.profile

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.youtubeclone.app.R
import com.youtubeclone.app.presentation.common.EmptyStateMessage
import com.youtubeclone.app.presentation.common.ErrorState
import com.youtubeclone.app.presentation.common.ErrorToast
import com.youtubeclone.app.presentation.common.LoadingState
import com.youtubeclone.app.presentation.home.previewVideo
import com.youtubeclone.app.presentation.theme.YoutubeCloneTheme
import com.youtubeclone.app.presentation.watch.SuggestedVideoCard
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** Listes personnelles ouvertes depuis l'onglet « Vous ». */
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

// Nombre de vidéos restantes sous l'écran à partir duquel la page suivante est demandée.
private const val LOAD_MORE_THRESHOLD = 5

@Composable
fun YouListScreen(
    onVideoClick: (videoId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: YouListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ErrorToast(viewModel.errors)

    // Rechargée à chaque retour sur l'écran : une vidéo regardée ou aimée entre-temps apparaît en tête.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.refresh() }
    }

    YouListContent(
        list = viewModel.list,
        state = state,
        onVideoClick = onVideoClick,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::refresh,
        modifier = modifier,
    )
}

@Composable
private fun YouListContent(
    list: YouList,
    state: YouListUiState,
    onVideoClick: (videoId: String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(list.title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        )
        val contentModifier = Modifier.weight(1f)
        when (state) {
            YouListUiState.Loading -> LoadingState(contentModifier)
            is YouListUiState.Error -> ErrorState(state.error, onRetry, contentModifier)
            YouListUiState.Empty -> Box(
                modifier = contentModifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                EmptyStateMessage(
                    icon = list.icon,
                    title = stringResource(list.emptyTitle),
                    message = stringResource(list.emptyMessage),
                )
            }
            is YouListUiState.Success -> VideoList(state, onVideoClick, onLoadMore, contentModifier)
        }
    }
}

// Cartes compactes, comme les suggestions de l'écran de lecture. La page suivante est demandée
// à l'approche du bas de la liste.
@Composable
private fun VideoList(
    state: YouListUiState.Success,
    onVideoClick: (videoId: String) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState, state.videos.size) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= state.videos.size - LOAD_MORE_THRESHOLD
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadMore() }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(state.videos, key = { it.id }) { video ->
            SuggestedVideoCard(video = video, modifier = Modifier.clickable { onVideoClick(video.id) })
        }
        if (state.isLoadingMore) {
            item(key = "loadingMore") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

@Preview(name = "Historique", showBackground = true, backgroundColor = 0xFF0F0F0F, heightDp = 500)
@Composable
private fun YouListContentPreview() {
    YoutubeCloneTheme {
        YouListContent(
            list = YouList.History,
            state = YouListUiState.Success(
                videos = listOf(
                    previewVideo("1", "Le ballet des méduses", "Studio Libre", 2_300, 7),
                    previewVideo("2", "Une demi-minute en ville", "Carnet de Route", 980, 12),
                ),
                canLoadMore = false,
            ),
            onVideoClick = {},
            onLoadMore = {},
            onRetry = {},
        )
    }
}

@Preview(name = "Vidéos aimées, vide", showBackground = true, backgroundColor = 0xFF0F0F0F, heightDp = 500)
@Composable
private fun YouListEmptyPreview() {
    YoutubeCloneTheme {
        YouListContent(list = YouList.Liked, state = YouListUiState.Empty, onVideoClick = {}, onLoadMore = {}, onRetry = {})
    }
}
