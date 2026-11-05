package com.youtubeclone.app.presentation.live

import android.view.View
import androidx.annotation.OptIn
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.LiveAuthor
import com.youtubeclone.app.domain.model.LiveChatMessage
import com.youtubeclone.app.presentation.common.ChannelAvatar
import com.youtubeclone.app.presentation.common.ErrorState
import com.youtubeclone.app.presentation.common.ErrorToast
import com.youtubeclone.app.presentation.common.LoadingState

/**
 * Écran d'un live (ADR-029) : la vidéo en haut, avec « EN DIRECT » et les spectateurs, puis le
 * titre et la chaîne, et en dessous le chat. Un visiteur lit sans pouvoir écrire ; le diffuseur
 * peut terminer le live.
 */
@Composable
fun LiveScreen(
    onBack: () -> Unit,
    onLoginRequired: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LiveViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    ErrorToast(viewModel.errors)
    // Salle ouverte seulement écran visible : le compteur de spectateurs est celui des gens qui regardent.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.collectLiveUpdates() }
    }
    LifecycleStartEffect(viewModel) {
        viewModel.onShown(true)
        onStopOrDispose { viewModel.onShown(false) }
    }
    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text(stringResource(R.string.live_end_title)) },
            text = { Text(stringResource(R.string.live_end_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmEnd = false
                    viewModel.endLive()
                }) { Text(stringResource(R.string.live_end_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text(stringResource(R.string.live_end_cancel)) } },
        )
    }

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        when (val current = state) {
            LiveUiState.Loading -> {
                Row(Modifier.fillMaxWidth().padding(4.dp)) { BackButton(onBack) }
                LoadingState(Modifier.weight(1f).fillMaxWidth())
            }
            is LiveUiState.Error -> {
                Row(Modifier.fillMaxWidth().padding(4.dp)) { BackButton(onBack) }
                ErrorState(current.error, viewModel::load, Modifier.weight(1f).fillMaxWidth())
            }
            is LiveUiState.Ready -> {
                LiveVideo(current, viewModel, onBack)
                LiveInfo(current, onEnd = { confirmEnd = true })
                // Les émojis des réactions remontent par-dessus le chat, sans le masquer ni capter les appuis.
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Chat(current, Modifier.fillMaxSize())
                    if (!current.isEnded) {
                        FloatingReactions(viewModel.reactions, Modifier.matchParentSize())
                    }
                }
                if (current.isEnded) {
                    Text(
                        text = stringResource(R.string.live_chat_closed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                } else if (current.myId == null) {
                    // Visiteur : il lit le chat, la connexion est demandée au moment d'écrire.
                    OutlinedButton(
                        onClick = onLoginRequired,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    ) { Text(stringResource(R.string.live_chat_login)) }
                } else {
                    ReactionBar(onReact = viewModel::react)
                    ChatInput(draft, isSending, viewModel::onDraftChange, viewModel::send)
                }
            }
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit, tint: Color = MaterialTheme.colorScheme.onBackground) {
    IconButton(onClick = onBack) {
        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back), tint = tint)
    }
}

// Vidéo 16/9 en haut : flèche de retour, « EN DIRECT » et spectateurs en surcouche. Live terminé : un voile.
@OptIn(UnstableApi::class)
@Composable
private fun LiveVideo(state: LiveUiState.Ready, viewModel: LiveViewModel, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = viewModel.player
                    // Pas de contrôles : un direct ne se met pas en pause et n'a pas de barre de progression.
                    useController = false
                    keepScreenOn = true
                }
            },
            onRelease = { view -> view.player = null },
            modifier = Modifier.matchParentSize(),
        )
        if (state.isEnded) {
            Box(
                modifier = Modifier.matchParentSize().background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = state.live.stream?.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.25f,
                    modifier = Modifier.matchParentSize(),
                )
                Text(
                    text = stringResource(R.string.live_ended),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopStart).padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onBack, tint = Color.White)
            if (!state.isEnded) {
                LiveBadge()
                Box(Modifier.weight(1f))
                ViewerCountPill(state.viewerCount)
            }
        }
    }
}

@Composable
private fun LiveInfo(state: LiveUiState.Ready, onEnd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ChannelAvatar(channel = state.live.channel, modifier = Modifier.size(36.dp).clip(CircleShape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = state.live.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = state.live.channel.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (state.isBroadcaster && !state.isEnded) {
            Button(onClick = onEnd) { Text(stringResource(R.string.live_end_button)) }
        }
    }
}

@Composable
private fun Chat(state: LiveUiState.Ready, modifier: Modifier = Modifier) {
    if (state.messages.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.live_chat_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val listState = rememberLazyListState()
    // Un nouveau message ramène en bas, si l'on y était à peu près.
    LaunchedEffect(state.messages.firstOrNull()?.id) {
        if (listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0)
    }
    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        // Calé en bas, comme un chat : peu de messages restent près du champ de saisie.
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
    ) {
        items(state.messages, key = { it.id }) { message -> ChatLine(message) }
    }
}

@Composable
private fun ChatLine(message: LiveChatMessage) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        ChatAvatar(message.author)
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    append(message.author.displayName)
                }
                append("  ")
                append(message.content)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun ChatAvatar(author: LiveAuthor) {
    val modifier = Modifier.size(28.dp).clip(CircleShape)
    if (author.avatarUrl != null) {
        AsyncImage(author.avatarUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Text(
                text = author.displayName.take(1).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun ChatInput(draft: String, isSending: Boolean, onChange: (String) -> Unit, onSend: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onChange,
            placeholder = { Text(stringResource(R.string.live_chat_placeholder)) },
            maxLines = 3,
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSend, enabled = draft.isNotBlank() && !isSending) {
            Icon(painterResource(R.drawable.ic_send), contentDescription = stringResource(R.string.messages_send))
        }
    }
}
