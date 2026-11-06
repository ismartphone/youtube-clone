package com.youtubeclone.app.presentation.playlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.youtubeclone.app.R
import com.youtubeclone.app.presentation.theme.redTextButtonColors
import com.youtubeclone.app.domain.model.PLAYLIST_TITLE_MAX_LENGTH
import com.youtubeclone.app.presentation.common.ErrorState
import com.youtubeclone.app.presentation.common.ErrorToast
import com.youtubeclone.app.presentation.common.LoadingState

/** Feuille « Enregistrer » : ajouter la vidéo [videoId] à ses playlists, ou à une nouvelle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveToPlaylistSheet(
    videoId: String,
    onDismiss: () -> Unit,
    viewModel: SaveToPlaylistViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreate by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(videoId) { viewModel.load(videoId) }
    ErrorToast(viewModel.errors)

    if (showCreate) {
        PlaylistTitleDialog(
            title = stringResource(R.string.playlist_new),
            confirmLabel = stringResource(R.string.playlist_create),
            onConfirm = {
                viewModel.create(it)
                showCreate = false
            },
            onDismiss = { showCreate = false },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Text(
                text = stringResource(R.string.playlist_save_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            when (val current = state) {
                SaveToPlaylistUiState.Loading -> LoadingState(Modifier.fillMaxWidth().heightIn(min = 120.dp))
                is SaveToPlaylistUiState.Error -> ErrorState(
                    current.error,
                    { viewModel.load(videoId) },
                    Modifier.fillMaxWidth().heightIn(min = 120.dp),
                )
                is SaveToPlaylistUiState.Ready -> {
                    if (current.playlists.isEmpty()) {
                        Text(
                            text = stringResource(R.string.playlist_save_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                    }
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(current.playlists, key = { it.id }) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = playlist.containsVideo,
                                        enabled = playlist.id !in current.pending,
                                        role = Role.Checkbox,
                                        onValueChange = { viewModel.toggle(playlist) },
                                    )
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (playlist.id in current.pending) {
                                    CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                                } else {
                                    Checkbox(checked = playlist.containsVideo, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(playlist.title, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        text = pluralStringResource(R.plurals.playlist_video_count, playlist.videoCount, playlist.videoCount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    TextButton(colors = redTextButtonColors(), onClick = { showCreate = true }, modifier = Modifier.padding(horizontal = 12.dp)) {
                        // Décoratif (ADR-030) : icône du bouton « Nouvelle playlist » : son libellé est lu.
                        Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.playlist_new), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

/** Saisie du titre d'une playlist, pour la créer ou la renommer. */
@Composable
internal fun PlaylistTitleDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initial: String = "",
) {
    var value by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(PLAYLIST_TITLE_MAX_LENGTH) },
                label = { Text(stringResource(R.string.playlist_title_label)) },
                supportingText = { Text("${value.length} / $PLAYLIST_TITLE_MAX_LENGTH") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(colors = redTextButtonColors(), onClick = { onConfirm(value) }, enabled = value.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(colors = redTextButtonColors(), onClick = onDismiss) { Text(stringResource(R.string.playlist_cancel)) } },
    )
}
