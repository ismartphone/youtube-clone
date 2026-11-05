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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.youtubeclone.app.R
import com.youtubeclone.app.presentation.theme.YoutubeRedFill
import com.youtubeclone.app.domain.model.Category
import com.youtubeclone.app.domain.model.Live
import com.youtubeclone.app.domain.model.Post
import com.youtubeclone.app.domain.model.User
import com.youtubeclone.app.domain.model.VideoAd
import com.youtubeclone.app.presentation.ads.SponsoredAdCard
import com.youtubeclone.app.presentation.channel.PostCard
import com.youtubeclone.app.presentation.common.AppLogo
import com.youtubeclone.app.presentation.common.ErrorState
import com.youtubeclone.app.presentation.common.LocalNowPlayingActive
import com.youtubeclone.app.presentation.common.MessageState
import com.youtubeclone.app.presentation.common.VideoListSkeleton
import com.youtubeclone.app.presentation.common.minTouchTarget
import com.youtubeclone.app.presentation.common.rememberStaggeredAppearance
import com.youtubeclone.app.presentation.common.staggeredAppearance
import com.youtubeclone.app.presentation.live.LiveCard
import com.youtubeclone.app.presentation.theme.LocalReduceMotion
import com.youtubeclone.app.presentation.theme.Motion
import com.youtubeclone.app.presentation.theme.YoutubeCloneTheme
import kotlinx.coroutines.flow.distinctUntilChanged

// Écran d'accueil d'après les maquettes (android/maquettes/accueil-*.png, captures iOS) :
// on en reprend la mise en page, les proportions et la hiérarchie, avec les conventions
// Android et Material (police Roboto, barre de navigation Android, zones tactiles de 48 dp).

private val TopBarHeight = 48.dp
private val ChipHeight = 32.dp

// Le post mis en avant vient après les trois premières vidéos du fil.
private const val VIDEOS_BEFORE_POST = 3

private const val SPONSORED_KEY = "sponsored"

/** Vidéo du bandeau sponsorisé : lecteur, sourdine, miniature tant que la vidéo n'a pas d'image. */
private data class SponsoredAdPreviewState(val player: Player, val muted: Boolean, val coverWithThumbnail: Boolean)

/** Vrai si l'élément [key] de la liste est visible au moins à moitié. */
private fun LazyListState.isHalfVisible(key: Any): Boolean {
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return false
    val visibleTop = maxOf(item.offset, info.viewportStartOffset)
    val visibleBottom = minOf(item.offset + item.size, info.viewportEndOffset)
    return item.size > 0 && (visibleBottom - visibleTop) * 2 >= item.size
}

@Composable
fun HomeScreen(
    onVideoClick: (videoId: String) -> Unit,
    onChannelClick: (channelId: String) -> Unit,
    onAccountClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSponsoredAdPlay: (VideoAd) -> Unit,
    onRemoveAdsClick: () -> Unit,
    onPostClick: (channelId: String) -> Unit,
    onMessagesClick: () -> Unit,
    onLoginRequired: () -> Unit,
    onLiveClick: (liveId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val unreadMessages by viewModel.unreadMessages.collectAsStateWithLifecycle()
    val latestPost by viewModel.latestPost.collectAsStateWithLifecycle()
    val lives by viewModel.lives.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sponsoredAd by viewModel.sponsoredAd.collectAsStateWithLifecycle()
    val adMuted by viewModel.adPreview.muted.collectAsStateWithLifecycle()
    val adFirstFrameShown by viewModel.adPreview.firstFrameShown.collectAsStateWithLifecycle()
    val adFailed by viewModel.adPreview.failed.collectAsStateWithLifecycle()
    // Fil quitté (autre onglet, autre écran, arrière-plan) : la vidéo du bandeau s'arrête.
    // Un lecteur ouvert (écran de lecture ou mini-lecteur, ADR-024) suspend aussi le bandeau : deux
    // vidéos en même temps, c'est deux décodeurs.
    val nowPlaying by rememberUpdatedState(LocalNowPlayingActive.current)
    // Retour sur le fil (autre écran, arrière-plan) : les lives et leurs spectateurs sont relus (ADR-029).
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        viewModel.onFeedShown(!nowPlaying)
        viewModel.refreshLives()
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(nowPlaying) {
        viewModel.onFeedShown(!nowPlaying && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onFeedShown(false) }
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
        sponsoredAdPreview = SponsoredAdPreviewState(viewModel.adPreview.player, adMuted, !adFirstFrameShown || adFailed),
        onSponsoredAdClick = viewModel::onSponsoredAdClicked,
        onSponsoredAdPlay = {
            viewModel.onSponsoredAdWatchClicked(it)
            onSponsoredAdPlay(it)
        },
        onToggleSponsoredAdSound = viewModel::onToggleSponsoredAdSound,
        onRemoveAdsClick = {
            viewModel.onRemoveAdsClicked(it)
            onRemoveAdsClick()
        },
        onSponsoredAdVisibilityChanged = viewModel::onSponsoredAdVisibilityChanged,
        latestPost = latestPost,
        onPostClick = onPostClick,
        unreadMessages = unreadMessages,
        onMessagesClick = onMessagesClick,
        onLoginRequired = onLoginRequired,
        lives = lives,
        onLiveClick = onLiveClick,
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
    sponsoredAdPreview: SponsoredAdPreviewState? = null,
    onSponsoredAdClick: (VideoAd) -> Unit = {},
    onSponsoredAdPlay: (VideoAd) -> Unit = {},
    onToggleSponsoredAdSound: (VideoAd) -> Unit = {},
    onRemoveAdsClick: (VideoAd) -> Unit = {},
    onSponsoredAdVisibilityChanged: (Boolean) -> Unit = {},
    latestPost: Post? = null,
    onPostClick: (channelId: String) -> Unit = {},
    unreadMessages: Int = 0,
    onMessagesClick: () -> Unit = {},
    onLoginRequired: () -> Unit = {},
    lives: List<Live> = emptyList(),
    onLiveClick: (liveId: String) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        HomeTopBar(
            account = account,
            unreadMessages = unreadMessages,
            onSearchClick = onSearchClick,
            // Un visiteur qui touche la messagerie est invité à se connecter.
            onMessagesClick = if (account != null) onMessagesClick else onLoginRequired,
            onAccountClick = onAccountClick,
        )
        CategoryFilters(filters = filters, onSelect = onCategorySelect)
        when (uiState) {
            HomeUiState.Loading -> VideoListSkeleton()
            HomeUiState.Empty -> MessageState(stringResource(R.string.home_empty))
            is HomeUiState.Error -> ErrorState(uiState.error, onRetry)
            is HomeUiState.Success -> {
                // Nouvelle liste chargée : ses premières cartes apparaissent l'une après l'autre.
                val appearance = rememberStaggeredAppearance(uiState)
                val listState = rememberLazyListState()
                // Carte sponsorisée visible au moins à moitié : sa vidéo joue ; sinon elle s'arrête,
                // pour ne pas faire ramer le défilement.
                LaunchedEffect(listState, sponsoredAd != null) {
                    snapshotFlow { listState.isHalfVisible(SPONSORED_KEY) }
                        .distinctUntilChanged()
                        .collect(onSponsoredAdVisibilityChanged)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(VideoCardSpacing),
                    contentPadding = PaddingValues(top = 4.dp, bottom = VideoCardSpacing),
                ) {
                    // Lives en cours (ADR-029), tout en haut du fil : avant même le bandeau sponsorisé.
                    items(lives, key = { "live-" + it.id }) { live ->
                        LiveCard(live = live, onClick = { onLiveClick(live.id) })
                    }
                    // Bandeau sponsorisé (ADR-021), jamais pour un compte Premium.
                    if (sponsoredAd != null && sponsoredAdPreview != null) {
                        item(key = SPONSORED_KEY) {
                            SponsoredAdCard(
                                ad = sponsoredAd,
                                player = sponsoredAdPreview.player,
                                muted = sponsoredAdPreview.muted,
                                coverWithThumbnail = sponsoredAdPreview.coverWithThumbnail,
                                onToggleMute = { onToggleSponsoredAdSound(sponsoredAd) },
                                onWatch = { onSponsoredAdPlay(sponsoredAd) },
                                onSiteClick = { onSponsoredAdClick(sponsoredAd) },
                                onRemoveAdsClick = { onRemoveAdsClick(sponsoredAd) },
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
private fun HomeTopBar(
    account: User?,
    unreadMessages: Int,
    onSearchClick: () -> Unit,
    onMessagesClick: () -> Unit,
    onAccountClick: () -> Unit,
) {
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
            // Titre de l'écran pour TalkBack (ADR-030) : annoncé comme un titre.
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSearchClick) {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = stringResource(R.string.home_search),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        // Messagerie (ADR-025), à gauche de l'avatar, avec la pastille des messages non lus.
        IconButton(onClick = onMessagesClick) {
            BadgedBox(
                badge = {
                    if (unreadMessages > 0) {
                        Badge { Text(if (unreadMessages > 99) "99+" else unreadMessages.toString()) }
                    }
                },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_messages),
                    contentDescription = if (unreadMessages > 0) {
                        pluralStringResource(R.plurals.messages_unread, unreadMessages, unreadMessages)
                    } else {
                        stringResource(R.string.messages_open)
                    },
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
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
            modifier = avatarModifier.background(YoutubeRedFill),
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
        // Pas de marge verticale : chaque pastille occupe déjà 48 dp (ADR-030), soit la même hauteur qu'avant.
        contentPadding = PaddingValues(horizontal = 12.dp),
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
    // Zone cliquable de 48 dp (ADR-030) ; la pastille garde ses 32 dp, centrée dedans.
    Box(
        modifier = Modifier
            .minTouchTarget()
            .clip(RoundedCornerShape(8.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(ChipHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(container)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = content)
        }
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
