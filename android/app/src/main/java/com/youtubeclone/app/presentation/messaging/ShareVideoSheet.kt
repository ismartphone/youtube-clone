package com.youtubeclone.app.presentation.messaging

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.Conversation
import com.youtubeclone.app.domain.model.Video
import com.youtubeclone.app.domain.usecase.MessagingUseCases
import com.youtubeclone.app.presentation.common.ErrorToast
import com.youtubeclone.app.presentation.common.UiError
import com.youtubeclone.app.presentation.common.shareVideo
import com.youtubeclone.app.presentation.common.toUiError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Conversations récentes proposées par « Envoyer à » : les 5 plus récentes (ADR-025, décision 7). */
private const val RECENT_CONVERSATIONS = 5

data class ShareVideoUiState(
    val recent: List<Conversation> = emptyList(),
    val isLoading: Boolean = true,
    /** Conversation vers laquelle la vidéo part : sa ligne attend. */
    val sendingTo: String? = null,
)

@HiltViewModel
class ShareVideoViewModel @Inject constructor(
    private val messaging: MessagingUseCases,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShareVideoUiState())
    val uiState: StateFlow<ShareVideoUiState> = _uiState.asStateFlow()

    private val _sent = Channel<String>(Channel.BUFFERED)
    /** Nom du destinataire, une fois la vidéo envoyée. */
    val sent: Flow<String> = _sent.receiveAsFlow()

    private val _errors = MutableSharedFlow<UiError>(extraBufferCapacity = 1)
    val errors: SharedFlow<UiError> = _errors.asSharedFlow()

    init {
        viewModelScope.launch {
            // Sans réseau, la feuille garde ses autres choix : chercher quelqu'un, autres applications.
            val recent = runCatching { messaging.repository.getConversations() }.getOrDefault(emptyList())
            _uiState.value = ShareVideoUiState(recent = recent.take(RECENT_CONVERSATIONS), isLoading = false)
        }
    }

    fun send(conversation: Conversation, videoId: String) {
        if (_uiState.value.sendingTo != null) return
        _uiState.value = _uiState.value.copy(sendingTo = conversation.id)
        viewModelScope.launch {
            try {
                messaging.repository.sendMessage(conversation.id, content = "", videoId = videoId)
                _sent.send(conversation.other.displayName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errors.tryEmit(e.toUiError())
            } finally {
                _uiState.value = _uiState.value.copy(sendingTo = null)
            }
        }
    }
}

/**
 * « Envoyer à » (ADR-025, étape 2), ouvert par le bouton Partager d'une vidéo pour un compte
 * connecté : les 5 conversations les plus récentes, puis un lien pour chercher quelqu'un d'autre,
 * puis le partage Android habituel. [onSent] reçoit le nom du destinataire.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareVideoSheet(
    videoId: String,
    onSearchSomeoneElse: () -> Unit,
    onOtherApps: () -> Unit,
    onSent: (recipientName: String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: ShareVideoViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ErrorToast(viewModel.errors)
    LaunchedEffect(viewModel) {
        viewModel.sent.collect { name ->
            onSent(name)
            onDismiss()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Text(
                text = stringResource(R.string.share_send_to),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().heightIn(min = 72.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
            state.recent.forEach { conversation ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = state.sendingTo == null, role = Role.Button) {
                            viewModel.send(conversation, videoId)
                        }
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    PersonAvatar(conversation.other, size = 40.dp)
                    Text(
                        text = conversation.other.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.sendingTo == conversation.id) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SheetAction(R.drawable.ic_search, R.string.share_search_someone) {
                onDismiss()
                onSearchSomeoneElse()
            }
            SheetAction(R.drawable.ic_share, R.string.share_other_apps) {
                onDismiss()
                onOtherApps()
            }
        }
    }
}

@Composable
private fun SheetAction(icon: Int, label: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Décoratif (ADR-030) : icône de la ligne : le libellé de la ligne est lu.
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.padding(start = 8.dp).size(24.dp))
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Bouton Partager d'une vidéo (ADR-025, étape 2) : [share] ouvre « Envoyer à » pour un compte
 * connecté, directement le partage Android pour un visiteur (la messagerie exige un compte).
 * À placer dans l'écran ; [onSearchSomeoneElse] ouvre la recherche d'un destinataire.
 */
class VideoSharing internal constructor(val share: (Video) -> Unit)

@Composable
fun rememberVideoSharing(isLoggedIn: Boolean, onSearchSomeoneElse: (videoId: String) -> Unit): VideoSharing {
    val context = LocalContext.current
    var sharing by remember { mutableStateOf<Video?>(null) }
    sharing?.let { video ->
        ShareVideoSheet(
            videoId = video.id,
            onSearchSomeoneElse = { onSearchSomeoneElse(video.id) },
            onOtherApps = { shareVideo(context, video) },
            onSent = { name -> Toast.makeText(context, context.getString(R.string.share_sent, name), Toast.LENGTH_SHORT).show() },
            onDismiss = { sharing = null },
        )
    }
    val currentLoggedIn by rememberUpdatedState(isLoggedIn)
    return remember { VideoSharing { video -> if (currentLoggedIn) sharing = video else shareVideo(context, video) } }
}
