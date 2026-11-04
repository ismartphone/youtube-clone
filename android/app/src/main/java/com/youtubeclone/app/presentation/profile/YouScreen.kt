package com.youtubeclone.app.presentation.profile

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.Channel
import com.youtubeclone.app.domain.model.PlaylistSummary
import com.youtubeclone.app.domain.model.PremiumStatus
import com.youtubeclone.app.domain.model.User
import com.youtubeclone.app.presentation.common.ErrorState
import com.youtubeclone.app.presentation.common.ErrorToast
import com.youtubeclone.app.presentation.common.LoadingState
import com.youtubeclone.app.presentation.common.formatLongDate
import com.youtubeclone.app.presentation.theme.YoutubeCloneTheme

// Onglet « Vous » d'après les maquettes (android/maquettes/profil-02, -05 et -13, captures iOS) :
// en-tête du compte, raccourcis, puis réglages. Un visiteur voit une invitation à se connecter
// à la place de l'en-tête. On reprend la mise en page, avec les conventions Material.
// Les playlists des maquettes sont hors périmètre (CLAUDE.md) : pas de section.

/** Actions de l'onglet, regroupées pour garder des signatures lisibles. */
class YouActions(
    val onLoginClick: () -> Unit = {},
    val onRegisterClick: () -> Unit = {},
    val onChannelClick: (channelId: String) -> Unit = {},
    val onCustomizeChannel: () -> Unit = {},
    val onListClick: (YouList) -> Unit = {},
    val onSubscriptionsClick: () -> Unit = {},
    val onEditInterests: () -> Unit = {},
    val onHelpClick: () -> Unit = {},
    val onPlaylistClick: (playlistId: String) -> Unit = {},
    val onDownloadsClick: () -> Unit = {},
    val onLogoutClick: () -> Unit = {},
    val onPremiumClick: () -> Unit = {},
    val onCancelPremium: () -> Unit = {},
    val onSettingsClick: () -> Unit = {},
    val onCreateChannel: () -> Unit = {},
)

@Composable
fun YouScreen(
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onEditInterests: () -> Unit,
    onChannelClick: (channelId: String) -> Unit,
    onCustomizeChannel: () -> Unit,
    onHelpClick: () -> Unit,
    onPlaylistClick: (playlistId: String) -> Unit,
    onDownloadsClick: () -> Unit,
    onListClick: (YouList) -> Unit,
    onSubscriptionsClick: () -> Unit,
    onPremiumClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onCreateChannel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: YouViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val premium by viewModel.premium.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    ErrorToast(viewModel.premiumMessages)
    val actions = YouActions(
        onLoginClick = onLoginClick,
        onRegisterClick = onRegisterClick,
        onChannelClick = onChannelClick,
        onCustomizeChannel = onCustomizeChannel,
        onListClick = onListClick,
        onSubscriptionsClick = onSubscriptionsClick,
        onEditInterests = onEditInterests,
        onHelpClick = onHelpClick,
        onPlaylistClick = onPlaylistClick,
        onDownloadsClick = onDownloadsClick,
        onLogoutClick = viewModel::onLogoutClick,
        onPremiumClick = onPremiumClick,
        onCancelPremium = viewModel::onCancelPremium,
        onSettingsClick = onSettingsClick,
        onCreateChannel = onCreateChannel,
    )

    when (val current = state) {
        YouUiState.Loading -> LoadingState(modifier)
        // Sans réseau, les téléchargements restent accessibles (ADR-019).
        is YouUiState.Error -> Column(modifier = modifier.fillMaxSize()) {
            ErrorState(current.error, onRetry = viewModel::load, modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = onDownloadsClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) { Text(stringResource(R.string.downloads_title)) }
        }
        YouUiState.LoggedOut -> YouContent(user = null, channel = null, premium = null, actions = actions, modifier = modifier)
        is YouUiState.LoggedIn -> YouContent(current.user, current.channel, premium, actions, modifier, playlists)
    }
}

/** [user] null : visiteur non connecté. [premium] null : état Premium inconnu ou visiteur. */
@Composable
private fun YouContent(
    user: User?,
    channel: Channel?,
    premium: PremiumStatus?,
    actions: YouActions,
    modifier: Modifier = Modifier,
    playlists: List<PlaylistSummary>? = null,
) {
    // Sans compte, les listes personnelles mènent à la connexion.
    fun openList(list: YouList) = if (user != null) actions.onListClick(list) else actions.onLoginClick()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "header") {
            if (user != null) {
                AccountHeader(
                    user = user,
                    channel = channel,
                    onViewChannel = { channel?.let { actions.onChannelClick(it.id) } },
                    onCreateChannel = actions.onCreateChannel,
                    onCustomizeChannel = actions.onCustomizeChannel,
                )
            } else {
                SignInPrompt(actions.onLoginClick, actions.onRegisterClick)
            }
        }
        item(key = "shortcuts") {
            Shortcuts(
                onHistory = { openList(YouList.History) },
                // Avec une chaîne, Vos vidéos ouvre sa page ; connecté sans chaîne, sa création ;
                // visiteur, un état vide l'explique.
                onYourVideos = {
                    when {
                        user != null && channel != null -> actions.onChannelClick(channel.id)
                        user != null -> actions.onCreateChannel()
                        else -> openList(YouList.YourVideos)
                    }
                },
                onLiked = { openList(YouList.Liked) },
                onSubscriptions = actions.onSubscriptionsClick,
                onDownloads = { if (user != null) actions.onDownloadsClick() else actions.onLoginClick() },
            )
        }
        if (user != null && playlists != null) {
            item(key = "playlists") { PlaylistsSection(playlists, actions.onPlaylistClick) }
        }
        item(key = "premium") { PremiumCard(premium, actions.onPremiumClick, actions.onCancelPremium) }
        item(key = "settings") { SettingsList(isLoggedIn = user != null, actions = actions) }
    }
}

// Section « Playlists » (ADR-019) : une carte par playlist, dans un ruban horizontal. Une
// playlist se crée depuis le bouton Enregistrer d'une vidéo.
@Composable
private fun PlaylistsSection(playlists: List<PlaylistSummary>, onPlaylistClick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.you_playlists),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (playlists.isEmpty()) {
            Text(
                text = stringResource(R.string.you_playlists_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    Column(
                        modifier = Modifier
                            .width(160.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button) { onPlaylistClick(playlist.id) },
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (playlist.thumbnailUrl != null) {
                                AsyncImage(
                                    model = playlist.thumbnailUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.matchParentSize(),
                                )
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.ic_save),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            text = playlist.title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = pluralStringResource(R.plurals.playlist_video_count, playlist.videoCount, playlist.videoCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// Lien de l'en-tête, comme « Créer une chaîne › » : texte suivi d'un chevron.
@Composable
private fun HeaderLink(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick, role = Role.Button),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(18.dp),
        )
    }
}

// Grand avatar, nom, puis « @handle · Voir la chaîne › ». Sans chaîne, l'email remplace le handle,
// suivi d'un lien « Créer une chaîne › ».
@Composable
private fun AccountHeader(
    user: User,
    channel: Channel?,
    onViewChannel: () -> Unit,
    onCreateChannel: () -> Unit,
    onCustomizeChannel: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Avatar(user)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = user.displayName,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (channel != null) "@${channel.handle}" else user.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (channel != null) {
                    Text(
                        text = " · ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = onViewChannel, role = Role.Button),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.you_view_channel),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_right),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            if (channel != null) {
                HeaderLink(stringResource(R.string.customize_channel_title), onCustomizeChannel)
            }
            if (channel == null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onCreateChannel, role = Role.Button),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.you_create_channel),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

// Photo de profil, ou initiale du nom si l'utilisateur n'en a pas.
@Composable
private fun Avatar(user: User) {
    val avatarModifier = Modifier
        .size(72.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.primary)
    if (user.avatarUrl != null) {
        AsyncImage(
            model = user.avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = avatarModifier,
        )
    } else {
        Box(modifier = avatarModifier, contentAlignment = Alignment.Center) {
            Text(
                text = user.displayName.take(1).uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

// Visiteur : illustration, message et bouton Se connecter, à la place de l'en-tête du compte.
@Composable
private fun SignInPrompt(onLoginClick: () -> Unit, onRegisterClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_folder),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(72.dp),
        )
        Text(
            text = stringResource(R.string.you_logged_out_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.you_logged_out_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onLoginClick,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onBackground,
                contentColor = MaterialTheme.colorScheme.background,
            ),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Text(stringResource(R.string.login_submit), fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onRegisterClick) {
            Text(stringResource(R.string.you_create_account), color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

private data class Shortcut(@DrawableRes val icon: Int, @StringRes val label: Int, val onClick: () -> Unit)

// Raccourcis défilants, en pastilles grises comme les actions de l'écran de lecture.
@Composable
private fun Shortcuts(
    onHistory: () -> Unit,
    onYourVideos: () -> Unit,
    onLiked: () -> Unit,
    onSubscriptions: () -> Unit,
    onDownloads: () -> Unit,
) {
    val shortcuts = listOf(
        Shortcut(R.drawable.ic_history, R.string.you_history, onHistory),
        Shortcut(R.drawable.ic_your_videos, R.string.you_your_videos, onYourVideos),
        Shortcut(R.drawable.ic_like, R.string.you_liked_videos, onLiked),
        Shortcut(R.drawable.ic_tab_subscriptions, R.string.tab_subscriptions, onSubscriptions),
        Shortcut(R.drawable.ic_download, R.string.downloads_title, onDownloads),
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(shortcuts, key = { it.label }) { shortcut ->
            Row(
                modifier = Modifier
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = shortcut.onClick, role = Role.Button)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(shortcut.icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(shortcut.label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

@Composable
private fun SettingsList(isLoggedIn: Boolean, actions: YouActions) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        SettingsRow(R.drawable.ic_interests, R.string.you_edit_interests, actions.onEditInterests)
        SettingsRow(R.drawable.ic_settings, R.string.you_settings, actions.onSettingsClick)
        SettingsRow(R.drawable.ic_help, R.string.you_help, actions.onHelpClick)
        if (isLoggedIn) {
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            SettingsRow(R.drawable.ic_logout, R.string.you_logout, actions.onLogoutClick)
        }
    }
}

@Composable
private fun SettingsRow(@DrawableRes icon: Int, @StringRes label: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

// Offre Premium : un encart pour la découvrir, ou l'état de l'abonnement avec sa date de fin
// et l'annulation (fin immédiate, après confirmation).
@Composable
private fun PremiumCard(premium: PremiumStatus?, onPremiumClick: () -> Unit, onCancelPremium: () -> Unit) {
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    val isPremium = premium?.isPremium == true
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_premium),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(if (isPremium) R.string.premium_active else R.string.premium_name),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = premium?.expiresAt?.takeIf { isPremium }
                        ?.let { stringResource(R.string.premium_active_until, formatLongDate(it)) }
                        ?: stringResource(R.string.premium_card_pitch),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isPremium) {
            OutlinedButton(
                onClick = { confirmCancel = true },
                shape = CircleShape,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.premium_cancel))
            }
        } else {
            Button(
                onClick = onPremiumClick,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.background,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.premium_discover), fontWeight = FontWeight.Bold)
            }
        }
    }
    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(stringResource(R.string.premium_cancel_confirm_title)) },
            text = { Text(stringResource(R.string.premium_cancel_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    onCancelPremium()
                }) { Text(stringResource(R.string.premium_cancel_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text(stringResource(R.string.premium_cancel_keep)) }
            },
        )
    }
}

@Preview(name = "Connecté", showBackground = true, backgroundColor = 0xFF0F0F0F, heightDp = 900)
@Composable
private fun YouContentLoggedInPreview() {
    YoutubeCloneTheme {
        YouContent(
            user = User(id = "u", email = "camille@example.com", displayName = "Camille", avatarUrl = null),
            channel = Channel(id = "c", name = "Carnet de Route", handle = "carnetderoute", avatarUrl = null),
            premium = PremiumStatus(isPremium = true, expiresAt = java.time.Instant.parse("2026-11-23T17:00:00Z")),
            actions = YouActions(),
        )
    }
}

@Preview(name = "Visiteur", showBackground = true, backgroundColor = 0xFF0F0F0F, heightDp = 900)
@Composable
private fun YouContentVisitorPreview() {
    YoutubeCloneTheme {
        YouContent(user = null, channel = null, premium = null, actions = YouActions())
    }
}
