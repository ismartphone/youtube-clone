package com.youtubeclone.app.presentation.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.Channel
import com.youtubeclone.app.domain.model.ChannelSearchResult
import com.youtubeclone.app.presentation.common.ChannelAvatar
import com.youtubeclone.app.presentation.common.EmptyStateMessage
import com.youtubeclone.app.presentation.common.ErrorState
import com.youtubeclone.app.presentation.common.ErrorToast
import com.youtubeclone.app.presentation.common.LoadingState
import com.youtubeclone.app.presentation.common.formatFollowerCount
import com.youtubeclone.app.presentation.common.isLargeText
import com.youtubeclone.app.presentation.home.previewVideo
import com.youtubeclone.app.presentation.theme.YoutubeCloneTheme
import com.youtubeclone.app.presentation.watch.SuggestedVideoCard
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

// Recherche d'après les maquettes (android/maquettes/recherche-01, -04 et -09) : champ en haut
// avec retour et effacement, puis les chaînes et les vidéos trouvées. Les filtres, l'historique
// de recherche et la recherche vocale des maquettes ne sont pas repris.

// Nombre de résultats restants sous l'écran à partir duquel la page suivante est demandée.
private const val LOAD_MORE_THRESHOLD = 5

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onVideoClick: (videoId: String) -> Unit,
    onChannelClick: (channelId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ErrorToast(viewModel.errors)

    // imePadding : les états se centrent, et la liste défile, au-dessus du clavier.
    Column(modifier = modifier.fillMaxSize().imePadding()) {
        SearchBar(
            query = query,
            onQueryChange = viewModel::onQueryChange,
            onSearch = viewModel::searchNow,
            onBack = onBack,
        )
        val contentModifier = Modifier.weight(1f)
        when (val current = state) {
            SearchUiState.Initial -> InitialState(contentModifier)
            SearchUiState.Loading -> LoadingState(contentModifier)
            is SearchUiState.Empty -> NoResults(current.query, contentModifier)
            is SearchUiState.Error -> ErrorState(current.error, viewModel::searchNow, contentModifier)
            is SearchUiState.Success -> Results(
                state = current,
                onVideoClick = onVideoClick,
                onChannelClick = onChannelClick,
                onLoadMore = viewModel::loadMore,
                modifier = contentModifier,
            )
        }
    }
}

// Le champ prend le focus à l'ouverture : le clavier s'affiche aussitôt.
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val hint = stringResource(R.string.search_hint)
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.search_back),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp) // grandit avec la police du système (ADR-030)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(22.dp))
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboard?.hide()
                            onSearch()
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .semantics { contentDescription = hint }, // nom du champ pour TalkBack (ADR-030),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = {
                        onQueryChange("")
                        focusRequester.requestFocus()
                        keyboard?.show()
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.search_clear),
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun InitialState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.search_initial),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NoResults(query: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        EmptyStateMessage(
            icon = R.drawable.ic_search,
            title = stringResource(R.string.search_no_results, query),
            message = stringResource(R.string.search_no_results_message),
        )
    }
}

// Chaînes d'abord, en petit nombre, puis les vidéos, qui se chargent page par page.
@Composable
private fun Results(
    state: SearchUiState.Success,
    onVideoClick: (videoId: String) -> Unit,
    onChannelClick: (channelId: String) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState, state.videos.size, state.channels.size) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= listState.layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadMore() }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        if (state.channels.isNotEmpty()) {
            item(key = "channels-title") { SectionTitle(R.string.search_section_channels) }
            items(state.channels, key = { "channel-${it.channel.id}" }) { result ->
                ChannelResultRow(result, onClick = { onChannelClick(result.channel.id) })
            }
        }
        if (state.videos.isNotEmpty()) {
            item(key = "videos-title") { SectionTitle(R.string.search_section_videos) }
            items(state.videos, key = { "video-${it.id}" }) { video ->
                SuggestedVideoCard(
                    video = video,
                    modifier = Modifier
                        .clickable(role = Role.Button) { onVideoClick(video.id) }
                        .padding(vertical = 6.dp),
                )
            }
        }
        if (state.isLoadingMore) {
            item(key = "loading-more") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp).semantics { heading() },
    )
}

@Composable
private fun ChannelResultRow(result: ChannelSearchResult, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ChannelAvatar(result.channel, Modifier.size(56.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = result.channel.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = if (isLargeText()) 3 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "@${result.channel.handle} · ${formatFollowerCount(result.followerCount)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (isLargeText()) 3 else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F0F0F, heightDp = 640)
@Composable
private fun ResultsPreview() {
    YoutubeCloneTheme {
        Results(
            state = SearchUiState.Success(
                videos = listOf(previewVideo("1", "Sintel, la bande-annonce", "Studio Libre", 12_400, 3)),
                channels = listOf(
                    ChannelSearchResult(Channel("c1", "Studio Libre", "studiolibre", null), followerCount = 3),
                ),
                canLoadMore = false,
            ),
            onVideoClick = {},
            onChannelClick = {},
            onLoadMore = {},
        )
    }
}
