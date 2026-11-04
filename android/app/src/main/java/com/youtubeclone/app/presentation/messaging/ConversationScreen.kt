package com.youtubeclone.app.presentation.messaging

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.Message
import com.youtubeclone.app.presentation.common.BackTopBar
import com.youtubeclone.app.presentation.common.ErrorState
import com.youtubeclone.app.presentation.common.ErrorToast
import com.youtubeclone.app.presentation.common.LoadingState
import kotlinx.coroutines.flow.distinctUntilChanged

// Bulles : les siennes à droite, en rouge ; celles de l'autre à gauche, en gris.
private val BubbleShape = RoundedCornerShape(18.dp)

/** Une conversation (ADR-025) : messages en bulles, le plus récent en bas, champ de saisie en bas. */
@Composable
fun ConversationScreen(
    onBack: () -> Unit,
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
    Column(modifier = modifier.fillMaxSize().imePadding()) {
        val ready = state as? ConversationUiState.Ready
        BackTopBar(title = ready?.other?.displayName.orEmpty(), onBack = onBack)
        when (val current = state) {
            ConversationUiState.Loading -> LoadingState(Modifier.weight(1f).fillMaxWidth())
            is ConversationUiState.Error -> ErrorState(current.error, viewModel::load, Modifier.weight(1f).fillMaxWidth())
            is ConversationUiState.Ready -> Messages(current, viewModel::loadMore, Modifier.weight(1f))
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
private fun Messages(state: ConversationUiState.Ready, onLoadMore: () -> Unit, modifier: Modifier = Modifier) {
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
            Bubble(message, mine = message.senderId == state.myId)
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

@Composable
private fun Bubble(message: Message, mine: Boolean) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            text = message.content,
            style = MaterialTheme.typography.bodyMedium,
            color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(BubbleShape)
                .background(if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

private const val LOAD_MORE_THRESHOLD = 5
