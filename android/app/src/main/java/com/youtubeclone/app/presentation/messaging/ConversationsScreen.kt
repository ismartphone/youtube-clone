package com.youtubeclone.app.presentation.messaging

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import com.youtubeclone.app.R
import com.youtubeclone.app.presentation.theme.YoutubeRedFill
import com.youtubeclone.app.domain.model.Conversation
import com.youtubeclone.app.domain.model.Person
import com.youtubeclone.app.presentation.common.BackTopBar
import com.youtubeclone.app.presentation.common.isLargeText
import com.youtubeclone.app.presentation.common.EmptyStateMessage
import com.youtubeclone.app.presentation.common.ErrorState
import com.youtubeclone.app.presentation.common.LoadingState
import com.youtubeclone.app.presentation.common.formatRelativeDate

/** Liste des conversations (ADR-025), ouverte par l'icône de messagerie du fil. */
@Composable
fun ConversationsScreen(
    onBack: () -> Unit,
    onConversationClick: (conversationId: String) -> Unit,
    onNewConversation: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConversationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Temps réel seulement écran ouvert (ADR-025, décision 2) : fermé dès qu'on le quitte.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.collectLiveUpdates() }
    }
    Column(modifier = modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackTopBar(title = stringResource(R.string.messages_title), onBack = onBack, modifier = Modifier.weight(1f))
            IconButton(onClick = onNewConversation, modifier = Modifier.padding(end = 8.dp)) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.messages_new))
            }
        }
        when (val current = state) {
            ConversationsUiState.Loading -> LoadingState(Modifier.fillMaxSize())
            is ConversationsUiState.Error -> ErrorState(current.error, viewModel::load, Modifier.fillMaxSize())
            is ConversationsUiState.Ready -> if (current.conversations.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyStateMessage(
                        icon = R.drawable.ic_messages,
                        title = stringResource(R.string.messages_empty_title),
                        message = stringResource(R.string.messages_empty_message),
                        modifier = Modifier.padding(32.dp),
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(current.conversations, key = { it.id }) { conversation ->
                        ConversationRow(conversation, current.myId) { onConversationClick(conversation.id) }
                    }
                }
            }
        }
    }
}

// Avatar, nom, dernier message (« Vous : » devant les siens), date ; en gras avec une pastille
// quand il reste des messages non lus.
@Composable
private fun ConversationRow(conversation: Conversation, myId: String, onClick: () -> Unit) {
    val unread = conversation.unreadCount > 0
    val large = isLargeText() // police très agrandie (ADR-030) : plus de lignes pour le nom et l'aperçu
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {} // une seule annonce : nom, dernier message, date, non-lus (ADR-030)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PersonAvatar(conversation.other, size = 48.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = conversation.other.displayName,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = if (large) 3 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            conversation.lastMessage?.let { last ->
                val mine = last.senderId == myId
                val text = when {
                    last.content.isNotEmpty() && mine -> stringResource(R.string.messages_you, last.content)
                    last.content.isNotEmpty() -> last.content
                    mine -> stringResource(R.string.messages_you_shared_video)
                    else -> stringResource(R.string.messages_shared_video)
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal),
                    color = if (unread) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (large) 3 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            conversation.lastMessage?.let {
                Text(
                    text = formatRelativeDate(it.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (unread) UnreadBadge(conversation.unreadCount)
        }
    }
}

/** Pastille rouge du nombre de non-lus, plafonné à « 99+ ». */
@Composable
internal fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(YoutubeRedFill)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** Photo de la personne, ou l'initiale de son nom à défaut. */
@Composable
internal fun PersonAvatar(person: Person, size: Dp) {
    val modifier = Modifier.size(size).clip(CircleShape)
    if (person.avatarUrl != null) {
        AsyncImage(
            model = person.avatarUrl,
            // Décoratif (ADR-030) : photo de la personne : son nom est lu avec la ligne.
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        )
    } else {
        Box(modifier = modifier.background(YoutubeRedFill), contentAlignment = Alignment.Center) {
            Text(
                text = person.displayName.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
