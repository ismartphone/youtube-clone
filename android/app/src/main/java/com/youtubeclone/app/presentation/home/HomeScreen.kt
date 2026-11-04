package com.youtubeclone.app.presentation.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.Category
import com.youtubeclone.app.domain.model.Post
import com.youtubeclone.app.domain.model.User
import com.youtubeclone.app.domain.model.VideoAd
import com.youtubeclone.app.presentation.ads.SponsoredAdCard
import com.youtubeclone.app.presentation.channel.PostCard
import com.youtubeclone.app.presentation.common.AppLogo
import com.youtubeclone.app.presentation.common.ErrorState
import com.youtubeclone.app.presentation.common.MessageState
import com.youtubeclone.app.presentation.common.VideoListSkeleton
import com.youtubeclone.app.presentation.common.rememberStaggeredAppearance
import com.youtubeclone.app.presentation.common.staggeredAppearance
import com.youtubeclone.app.presentation.theme.LocalReduceMotion
import com.youtubeclone.app.presentation.theme.Motion
import com.youtubeclone.app.presentation.theme.YoutubeCloneTheme

// Écran d'accueil d'après les maquettes (android/maquettes/accueil-*.png, captures iOS) :
// on en reprend la mise en page, les proportions et la hiérarchie, avec les conventions
// Android et Material (police Roboto, barre de navigation Android, zones tactiles de 48 dp).

private val TopBarHeight = 48.dp
private val ChipHeight = 32.dp

// Le post mis en avant vient après les trois premières vidéos du fil.
private const val VIDEOS_BEFORE_POST = 3

@Composable
fun HomeScreen(
    onVideoClick: (videoId: String) -> Unit,
    onChannelClick: (channelId: String) -> Unit,
    onAccountClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSponsoredAdPlay: (VideoAd) -> Unit,
    onRemoveAdsClick: () -> Unit,
    onPostClick: (channelId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val latestPost by viewModel.latestPost.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sponsoredAd by viewModel.sponsoredAd.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    HomeContent(
        uiState = uiState,
        filters = filters,
        account = account,
        onRetry = viewModel::load,
        onCategorySelect = viewModel::selectCategory,
        onVideoClick = onVideoClick,
        onChannelClick = onChannelClick,
        onAccountClick = onAccountClick,
        onSearchClick = onSearchClick,
        modifier = modifier,
        sponsoredAd = sponsoredAd,
        onSponsoredAdClick = viewModel::onSponsoredAdClicked,
        onSponsoredAdPlay = onSponsoredAdPlay,
        onRemoveAdsClick = {
            viewModel.onRemoveAdsClicked(it)
            onRemoveAdsClick()
        },
        onSponsoredAdShown = viewModel::onSponsoredAdShown,
        latestPost = latestPost,
        onPostClick = onPostClick,
    )
}

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    filters: HomeFilters,
    account: User?,
    onRetry: () -> Unit,
    onCategorySelect: (categoryId: String?) -> Unit,
    onVideoClick: (videoId: String) -> Unit,
    onChannelClick: (channelId: String) -> Unit,
    onAccountClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    sponsoredAd: VideoAd? = null,
    onSponsoredAdClick: (VideoAd) -> Unit = {},
    onSponsoredAdPlay: (VideoAd) -> Unit = {},
    onRemoveAdsClick: (VideoAd) -> Unit = {},
    onSponsoredAdShown: (VideoAd) -> Unit = {},
    latestPost: Post? = null,
    onPostClick: (channelId: String) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        HomeTopBar(account = account, onSearchClick = onSearchClick, onAccountClick = onAccountClick)
        CategoryFilters(filters = filters, onSelect = onCategorySelect)
        when (uiState) {
            HomeUiState.Loading -> VideoListSkeleton()
            HomeUiState.Empty -> MessageState(stringResource(R.string.home_empty))
            is HomeUiState.Error -> ErrorState(uiState.error, onRetry)
            is HomeUiState.Success -> {
                // Nouvelle liste chargée : ses premières cartes apparaissent l'une après l'autre.
                val appearance = rememberStaggeredAppearance(uiState)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(VideoCardSpacing),
                    contentPadding = PaddingValues(top = 4.dp, bottom = VideoCardSpacing),
                ) {
                    // Bandeau sponsorisé (ADR-021), tout en haut ; jamais pour un compte Premium.
                    if (sponsoredAd != null) {
                        item(key = "sponsored") {
                            SponsoredAdCard(
                                ad = sponsoredAd,
                                onClick = { onSponsoredAdClick(sponsoredAd) },
                                onPlay = { onSponsoredAdPlay(sponsoredAd) },
                                onRemoveAdsClick = { onRemoveAdsClick(sponsoredAd) },
                                onShown = { onSponsoredAdShown(sponsoredAd) },
                            )
                        }
                    }
                    // Le post le plus récent s'insère après les premières vidéos.
                    val firstVideos = uiState.videos.take(VIDEOS_BEFORE_POST)
                    val otherVideos = uiState.videos.drop(VIDEOS_BEFORE_POST)
                    itemsIndexed(firstVideos, key = { _, video -> video.id }) { index, video ->
                        VideoCard(
                            video = video,
                            onChannelClick = { onChannelClick(video.channel.id) },
                            modifier = Modifier
                                .staggeredAppearance(appearance, video.id, index)
                                .clickable { onVideoClick(video.id) },
                        )
                    }
                    if (latestPost != null) {
                        item(key = "post-${latestPost.id}") {
                            PostCard(latestPost, onClick = { onPostClick(latestPost.channel.id) })
                        }
                    }
                    itemsIndexed(otherVideos, key = { _, video -> video.id }) { index, video ->
                        VideoCard(
                            video = video,
                            onChannelClick = { onChannelClick(video.channel.id) },
                            modifier = Modifier
                                .staggeredAppearance(appearance, video.id, index + VIDEOS_BEFORE_POST)
                                .clickable { onVideoClick(video.id) },
                        )
                    }
                }
            }
        }
    }
}

// Barre du haut : logo à gauche, recherche et avatar à droite, fond sombre, hauteur contenue.
@Composable
private fun HomeTopBar(account: User?, onSearchClick: () -> Unit, onAccountClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppLogo(height = 20.dp)
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.opening_wordmark),
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                letterSpacing = (-0.5).sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSearchClick) {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = stringResource(R.string.home_search),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        IconButton(onClick = onAccountClick) {
            AccountAvatar(account)
        }
    }
}

// Photo du compte, initiale à défaut, icône de compte pour un visiteur.
@Composable
private fun AccountAvatar(account: User?) {
    val description = stringResource(R.string.home_account)
    val avatarModifier = Modifier
        .size(28.dp)
        .clip(CircleShape)
    when {
        account == null -> Icon(
            painter = painterResource(R.drawable.ic_tab_you),
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(28.dp),
        )
        account.avatarUrl != null -> AsyncImage(
            model = account.avatarUrl,
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = avatarModifier.background(MaterialTheme.colorScheme.surfaceVariant),
        )
        else -> Box(
            modifier = avatarModifier.background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = account.displayName.take(1).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

// Filtres par catégorie, défilants à l'horizontale. La pastille active est en inverse.
@Composable
private fun CategoryFilters(filters: HomeFilters, onSelect: (categoryId: String?) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            FilterChip(
                label = stringResource(R.string.home_filter_all),
                selected = filters.selectedCategoryId == null,
                onClick = { onSelect(null) },
            )
        }
        items(filters.categories, key = { it.id }) { category ->
            FilterChip(
                label = category.name,
                selected = filters.selectedCategoryId == category.id,
                onClick = { onSelect(category.id) },
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val duration = if (LocalReduceMotion.current) Motion.REDUCED_FADE_MS else Motion.SHORT_MS
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(duration, easing = Motion.Easing),
        label = "chipContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
        animationSpec = tween(duration, easing = Motion.Easing),
        label = "chipContent",
    )
    Box(
        modifier = Modifier
            .height(ChipHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F0F0F, heightDp = 760)
@Composable
private fun HomeContentPreview() {
    YoutubeCloneTheme {
        HomeContent(
            uiState = HomeUiState.Success(
                listOf(
                    previewVideo("1", "Sintel, la bande-annonce", "Studio Libre", 12_400, 3),
                    previewVideo("2", "Une demi-minute en ville, vue depuis la vitre d'un train qui traverse le centre", "Carnet de Route", 980, 12),
                    previewVideo("3", "Le ballet des méduses", "Studio Libre", 2_300_000, 40),
                ),
            ),
            filters = HomeFilters(
                categories = listOf(Category("a", "Animation"), Category("n", "Nature"), Category("v", "Voyage")),
                selectedCategoryId = null,
            ),
            account = User(id = "u", email = "camille@example.com", displayName = "Camille", avatarUrl = null),
            onRetry = {},
            onCategorySelect = {},
            onVideoClick = {},
            onChannelClick = {},
            onAccountClick = {},
            onSearchClick = {},
        )
    }
}
