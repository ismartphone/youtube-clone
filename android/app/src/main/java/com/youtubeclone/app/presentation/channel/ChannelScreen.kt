package com.youtubeclone.app.presentation.channel

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.Channel
import com.youtubeclone.app.domain.model.ChannelPage
import com.youtubeclone.app.domain.model.NotificationLevel
import com.youtubeclone.app.domain.model.FollowStatus
import com.youtubeclone.app.domain.model.Post
import com.youtubeclone.app.domain.model.Video
import com.youtubeclone.app.domain.model.VideoFormat
import com.youtubeclone.app.presentation.common.BackTopBar
import com.youtubeclone.app.presentation.common.ChannelAvatar
import com.youtubeclone.app.presentation.common.ChannelPageSkeleton
import com.youtubeclone.app.presentation.common.EmptyStateMessage
import com.youtubeclone.app.presentation.common.ErrorState
import com.youtubeclone.app.presentation.common.ErrorToast
import com.youtubeclone.app.presentation.common.VisibilityBadge
import com.youtubeclone.app.presentation.common.formatFollowerCount
import com.youtubeclone.app.presentation.common.formatRelativeDate
import com.youtubeclone.app.presentation.common.formatVideoCount
import com.youtubeclone.app.presentation.common.formatViewCount
import com.youtubeclone.app.presentation.home.previewVideo
import com.youtubeclone.app.presentation.theme.YoutubeCloneTheme
import com.youtubeclone.app.presentation.watch.SuggestedVideoCard

// Page de chaîne d'après les maquettes (android/maquettes/chaine-01, -03 et -12, captures iOS) :
// bannière, en-tête, S'abonner, puis onglets Vidéos, Shorts et Posts. On reprend la mise en page et la
// hiérarchie, avec les conventions Material.

/** Onglets de la page : Vidéos et Shorts montrent chacun un format de vidéo, Posts les posts (ADR-015). */
internal enum class ChannelTab(
    @StringRes val label: Int,
    val format: VideoFormat?,
    @DrawableRes val emptyIcon: Int,
    @StringRes val emptyTitle: Int,
    @StringRes val emptyMessage: Int,
) {
    Videos(
        R.string.channel_tab_videos, VideoFormat.LONG, R.drawable.ic_your_videos,
        R.string.channel_videos_empty_title, R.string.channel_videos_empty_message,
    ),
    Shorts(
        R.string.channel_tab_shorts, VideoFormat.SHORT, R.drawable.ic_tab_shorts,
        R.string.channel_shorts_empty_title, R.string.channel_shorts_empty_message,
    ),
    Posts(
        R.string.channel_tab_posts, null, R.drawable.ic_comment,
        R.string.channel_posts_empty_title, R.string.channel_posts_empty_message,
    ),
}

@Composable
fun ChannelScreen(
    onBack: () -> Unit,
    onVideoClick: (videoId: String) -> Unit,
    onShortClick: (videoId: String) -> Unit,
    onLoginRequired: () -> Unit,
    onCustomizeClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChannelViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val followState by viewModel.follow.state.collectAsStateWithLifecycle()
    val posts by viewModel.posts.collectAsStateWithLifecycle()
    ErrorToast(viewModel.follow.errors)
    NotificationPermissionRequest(viewModel.follow.bellActivated)

    Column(modifier = modifier.fillMaxSize()) {
        BackTopBar(
            title = (uiState as? ChannelUiState.Success)?.page?.channel?.name.orEmpty(),
            onBack = onBack,
        )
        val contentModifier = Modifier.weight(1f)
        when (val state = uiState) {
            ChannelUiState.Loading -> ChannelPageSkeleton(contentModifier)
            is ChannelUiState.Error -> ErrorState(state.error, viewModel::load, contentModifier)
            is ChannelUiState.Success -> ChannelContent(
                page = state.page,
                posts = posts,
                followState = followState,
                // Visiteur : la connexion est demandée au moment de s'abonner.
                onFollowClick = { if (isLoggedIn) viewModel.follow.toggle() else onLoginRequired() },
                onNotificationLevelSelect = viewModel.follow::selectNotificationLevel,
                onCustomizeClick = onCustomizeClick,
                initialTab = viewModel.initialTab,
                onVideoClick = onVideoClick,
                onShortClick = onShortClick,
                modifier = contentModifier,
            )
        }
    }
}

// Une seule grille de deux colonnes : en-tête, onglets et cartes compactes occupent toute la
// largeur, les Shorts une colonne chacun.
@Composable
private fun ChannelContent(
    page: ChannelPage,
    posts: List<Post>,
    followState: FollowUiState?,
    onFollowClick: () -> Unit,
    onVideoClick: (videoId: String) -> Unit,
    onShortClick: (videoId: String) -> Unit,
    modifier: Modifier = Modifier,
    onNotificationLevelSelect: (NotificationLevel) -> Unit = {},
    onCustomizeClick: () -> Unit = {},
    initialTab: ChannelTab = ChannelTab.Videos,
) {
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }
    val tabVideos = page.videos.filter { it.format == selectedTab.format }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(ShortsGap),
        verticalArrangement = Arrangement.spacedBy(ShortsGap),
    ) {
        fullWidthItem("header") { ChannelHeader(page, followState, onFollowClick, onNotificationLevelSelect, onCustomizeClick) }
        fullWidthItem("tabs") {
            ChannelTabs(selected = selectedTab, onSelect = { selectedTab = it })
        }
        when {
            selectedTab == ChannelTab.Posts -> if (posts.isEmpty()) {
                fullWidthItem("empty") { TabEmptyState(selectedTab) }
            } else {
                items(items = posts, key = { it.id }, span = { GridItemSpan(maxLineSpan) }) { post ->
                    PostCard(post)
                }
            }
            tabVideos.isEmpty() -> fullWidthItem("empty") { TabEmptyState(selectedTab) }
            selectedTab == ChannelTab.Videos -> items(
                items = tabVideos,
                key = { it.id },
                span = { GridItemSpan(maxLineSpan) },
            ) { video ->
                SuggestedVideoCard(
                    video = video,
                    modifier = Modifier
                        .clickable { onVideoClick(video.id) }
                        .padding(vertical = 6.dp),
                )
            }
            // Un Short s'ouvre dans le lecteur vertical plein écran, sur ce Short.
            else -> items(items = tabVideos, key = { it.id }) { video ->
                ShortTile(video, Modifier.clickable { onShortClick(video.id) })
            }
        }
    }
}

private val ShortsGap = 2.dp

private fun LazyGridScope.fullWidthItem(key: String, content: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

@Composable
private fun ChannelHeader(
    page: ChannelPage,
    followState: FollowUiState?,
    onFollowClick: () -> Unit,
    onNotificationLevelSelect: (NotificationLevel) -> Unit,
    onCustomizeClick: () -> Unit,
) {
    val channel = page.channel
    // Le compteur suit le bouton : il change dès que l'abonnement est confirmé.
    val followerCount = followState?.followerCount ?: page.followStatus.followerCount
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Banner(page.bannerUrl)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ChannelAvatar(
                channel = channel,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape),
                initialStyle = MaterialTheme.typography.headlineMedium,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "@${channel.handle} · ${formatFollowerCount(followerCount)} · ${formatVideoCount(page.videos.size)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        page.description?.let { ExpandableDescription(it) }
        if (page.isOwner) {
            // Sa propre chaîne : on ne s'y abonne pas, on la personnalise (ADR-018).
            FilledTonalButton(onClick = onCustomizeClick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.customize_channel_title))
            }
        } else if (followState != null && followState.canFollow) {
            FollowButton(
                state = followState,
                onClick = onFollowClick,
                modifier = Modifier.fillMaxWidth(),
                onNotificationLevelSelect = onNotificationLevelSelect,
            )
        }
    }
}

// Bannière de la chaîne, recadrée au format du bandeau. Sans bannière (ou tant qu'elle se charge),
// la place est tenue par un aplat sobre.
@Composable
private fun Banner(bannerUrl: String?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(BANNER_RATIO)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (bannerUrl != null) {
            AsyncImage(
                model = bannerUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

private const val BANNER_RATIO = 3.6f

// Description sur deux lignes ; « …plus » n'apparaît que si le texte est coupé.
@Composable
private fun ExpandableDescription(description: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var isTruncated by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) isTruncated = it.hasVisualOverflow },
        )
        if (isTruncated || expanded) {
            Text(
                text = stringResource(if (expanded) R.string.watch_less else R.string.watch_more),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = { expanded = !expanded }, role = Role.Button),
            )
        }
    }
}

// Onglets avec le trait de sélection sous le libellé actif.
@Composable
private fun ChannelTabs(selected: ChannelTab, onSelect: (ChannelTab) -> Unit) {
    PrimaryTabRow(
        selectedTabIndex = selected.ordinal,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        indicator = {
            TabRowDefaults.PrimaryIndicator(
                modifier = Modifier.tabIndicatorOffset(selected.ordinal, matchContentSize = true),
                width = Dp.Unspecified,
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        divider = { HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant) },
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        ChannelTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                selectedContentColor = MaterialTheme.colorScheme.onBackground,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = {
                    Text(
                        text = stringResource(tab.label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (tab == selected) FontWeight.Bold else FontWeight.Medium,
                    )
                },
            )
        }
    }
}

// Miniature verticale d'un Short, vues en bas à gauche sur un léger dégradé.
@Composable
private fun ShortTile(video: Video, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(9f / 16f)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))),
        )
        VisibilityBadge(video.visibility, Modifier.align(Alignment.TopStart).padding(8.dp))
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatViewCount(video.viewCount),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

// Post : avatar, nom et date, texte, puis l'image éventuelle sur toute la largeur.
// Aussi dans le fil d'accueil et le fil Abonnements, où un appui ouvre les Posts de la chaîne.
@Composable
internal fun PostCard(post: Post, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChannelAvatar(post.channel, Modifier.size(32.dp), initialStyle = MaterialTheme.typography.labelLarge)
            Text(
                text = post.channel.name,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = formatRelativeDate(post.createdAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = post.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        post.imageUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp)),
            )
        }
    }
}

@Composable
private fun TabEmptyState(tab: ChannelTab) {
    EmptyStateMessage(
        icon = tab.emptyIcon,
        title = stringResource(tab.emptyTitle),
        message = stringResource(tab.emptyMessage),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
    )
}

private fun previewChannelPage(videos: List<Video>) = ChannelPage(
    channel = Channel(id = "c", name = "Studio Libre", handle = "studiolibre", avatarUrl = null),
    bannerUrl = null,
    description = "Courts métrages d'animation libres, making-of et extraits en haute définition. " +
        "Une nouvelle vidéo chaque semaine, et des Shorts entre deux.",
    followStatus = FollowStatus(followerCount = 15_800, isFollowing = false),
    isOwner = false,
    videos = videos,
)

@Preview(name = "Vidéos", showBackground = true, backgroundColor = 0xFF0F0F0F, heightDp = 900)
@Composable
private fun ChannelContentPreview() {
    YoutubeCloneTheme {
        ChannelContent(
            posts = emptyList(),
            page = previewChannelPage(
                listOf(
                    previewVideo("1", "Sintel, la bande-annonce", "Studio Libre", 12_400, 3),
                    previewVideo("2", "Big Buck Bunny, extrait", "Studio Libre", 980, 12),
                ),
            ),
            followState = FollowUiState(followerCount = 15_800, isFollowing = false),
            onFollowClick = {},
            onVideoClick = {},
            onShortClick = {},
        )
    }
}

@Preview(name = "Shorts vide", showBackground = true, backgroundColor = 0xFF0F0F0F, heightDp = 700)
@Composable
private fun ChannelShortsEmptyPreview() {
    YoutubeCloneTheme {
        ChannelContent(
            posts = emptyList(),
            page = previewChannelPage(emptyList()),
            followState = FollowUiState(followerCount = 15_800, isFollowing = true),
            onFollowClick = {},
            onVideoClick = {},
            onShortClick = {},
            initialTab = ChannelTab.Shorts,
        )
    }
}
