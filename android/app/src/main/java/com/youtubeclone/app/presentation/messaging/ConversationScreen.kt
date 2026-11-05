package com.youtubeclone.app.presentation.messaging

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.youtubeclone.app.R
import com.youtubeclone.app.presentation.theme.YoutubeRedFill
import com.youtubeclone.app.domain.model.Message
import com.youtubeclone.app.domain.model.SharedVideo
import com.youtubeclone.app.notifications.VisibleConversation
import com.youtubeclone.app.presentation.common.BackTopBar
import com.youtubeclone.app.presentation.common.ErrorState
import com.youtubeclone.app.presentation.common.ErrorToast
import com.youtubeclone.app.presentation.common.LoadingState
import com.youtubeclone.app.presentation.common.PremiumBadge
import com.youtubeclone.app.presentation.common.VideoThumbnailImage
import kotlinx.coroutines.flow.distinctUntilChanged

// Bulles : les siennes à droite, en rouge ; celles de l'autre à gauche, en gris.
private val BubbleShape = RoundedCornerShape(18.dp)

/** Une conversation (ADR-025) : messages en bulles, le plus récent en bas, champ de saisie en bas. */
@Composable
fun ConversationScreen(
    onBack: () -> Unit,
    onOpenVideo: (SharedVideo.Available) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConversationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    ErrorToast(viewModel.errors)
    // Temps réel seulement écran ouvert (ADR-025, décision 2) : fermé dès qu'on le quitte.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.collectLiveUpdates() }
    }
    // Conversation à l'écran : ses notifications ne s'affichent pas (ADR-025, décision 8).
    LifecycleStartEffect(viewModel.conversationId) {
        VisibleConversation.id = viewModel.conversationId
        onStopOrDispose { if (VisibleConversation.id == viewModel.conversationId) VisibleConversation.id = null }
    }
    Column(modifier = modifier.fillMaxSize().imePadding()) {
        val ready = state as? ConversationUiState.Ready
        BackTopBar(title = ready?.other?.displayName.orEmpty(), onBack = onBack)
        when (val current = state) {
            ConversationUiState.Loading -> LoadingState(Modifier.weight(1f).fillMaxWidth())
            is ConversationUiState.Error -> ErrorState(current.error, viewModel::load, Modifier.weight(1f).fillMaxWidth())
            is ConversationUiState.Ready -> Messages(current, viewModel::loadMore, onOpenVideo, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = viewModel::onDraftChange,
                placeholder = { Text(stringResource(R.string.messages_placeholder)) },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = viewModel::send, enabled = draft.isNotBlank() && !isSending && ready != null) {
                Icon(painterResource(R.drawable.ic_send), contentDescription = stringResource(R.string.messages_send))
            }
        }
    }
}

@Composable
private fun Messages(
    state: ConversationUiState.Ready,
    onLoadMore: () -> Unit,
    onOpenVideo: (SharedVideo.Available) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.messages.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.messages_conversation_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val listState = rememberLazyListState()
    // Liste à l'envers : le dernier élément visible est le plus ancien chargé ; près du haut,
    // la page précédente est demandée.
    LaunchedEffect(listState, state.messages.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last -> if (last >= state.messages.size - LOAD_MORE_THRESHOLD) onLoadMore() }
    }
    // Un nouveau message (reçu ou envoyé) ramène en bas, s'il en était proche.
    LaunchedEffect(state.messages.firstOrNull()?.id) {
        if (listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
    }
    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        // Calée en bas, comme toute conversation : peu de messages restent près du champ de saisie.
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
    ) {
        items(state.messages, key = { it.id }) { message ->
            MessageItem(message, mine = message.senderId == state.myId, onOpenVideo = onOpenVideo)
        }
        if (state.isLoadingMore) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp).size(20.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

// Vidéo partagée en carte, puis le texte éventuel en bulle, du même côté.
@Composable
private fun MessageItem(message: Message, mine: Boolean, onOpenVideo: (SharedVideo.Available) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        message.video?.let { SharedVideoCard(it, onOpenVideo) }
        if (message.content.isNotEmpty()) Bubble(message, mine)
    }
}

/**
 * Carte d'une vidéo partagée (ADR-025, étape 2) : miniature, titre et chaîne ; un appui l'ouvre.
 * Floutée si elle est Premium et le compte sans abonnement (ADR-026). Indisponible (supprimée,
 * ou privée) : une carte grise, sans rien révéler.
 */
@Composable
private fun SharedVideoCard(video: SharedVideo, onOpenVideo: (SharedVideo.Available) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .width(SharedVideoWidth)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (video is SharedVideo.Available) Modifier.clickable { onOpenVideo(video) } else Modifier),
    ) {
        when (video) {
            is SharedVideo.Available -> {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                    VideoThumbnailImage(video.thumbnailUrl, video.isPremium, Modifier.matchParentSize())
                    if (video.isPremium) PremiumBadge(Modifier.align(Alignment.TopStart).padding(6.dp))
                }
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = video.channelName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            SharedVideo.Unavailable -> Text(
                text = stringResource(R.string.messages_video_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun Bubble(message: Message, mine: Boolean) {
    Box(contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            text = message.content,
            style = MaterialTheme.typography.bodyMedium,
            color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(BubbleShape)
                .background(if (mine) YoutubeRedFill else MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

private const val LOAD_MORE_THRESHOLD = 5
private val SharedVideoWidth = 240.dp
