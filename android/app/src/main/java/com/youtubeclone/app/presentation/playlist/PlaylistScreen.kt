package com.youtubeclone.app.presentation.playlist

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.PlaylistDetail
import com.youtubeclone.app.presentation.common.BackTopBar
import com.youtubeclone.app.presentation.common.EmptyStateMessage
import com.youtubeclone.app.presentation.common.ErrorState
import com.youtubeclone.app.presentation.common.ErrorToast
import com.youtubeclone.app.presentation.common.LoadingState
import com.youtubeclone.app.presentation.watch.SuggestedVideoCard

/**
 * Une playlist (ADR-019) : ses vidéos dans l'ordre d'ajout, qu'on ouvre d'un toucher ou qu'on
 * retire ; renommer et supprimer la playlist.
 */
@Composable
fun PlaylistScreen(
    onBack: () -> Unit,
    onVideoClick: (videoId: String) -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showRename by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    ErrorToast(viewModel.errors)
    LaunchedEffect(viewModel) { viewModel.deleted.collect { onBack() } }

    val playlist = (state as? PlaylistUiState.Success)?.playlist
    if (showRename && playlist != null) {
        PlaylistTitleDialog(
            title = stringResource(R.string.playlist_rename),
            confirmLabel = stringResource(R.string.playlist_save),
            initial = playlist.title,
            onConfirm = {
                viewModel.rename(it)
                showRename = false
            },
            onDismiss = { showRename = false },
        )
    }
    if (showDelete && playlist != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.playlist_delete_title)) },
            text = { Text(stringResource(R.string.playlist_delete_message, playlist.title)) },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.playlist_delete)) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.playlist_cancel)) } },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = playlist?.title.orEmpty(), onBack = onBack)
        when (val current = state) {
            PlaylistUiState.Loading -> LoadingState(Modifier.weight(1f))
            is PlaylistUiState.Error -> ErrorState(current.error, viewModel::load, Modifier.weight(1f))
            is PlaylistUiState.Success -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item(key = "header") {
                    PlaylistHeader(
                        playlist = current.playlist,
                        onRename = { showRename = true },
                        onDelete = { showDelete = true },
                    )
                }
                if (current.playlist.videos.isEmpty()) {
                    item(key = "empty") {
                        EmptyStateMessage(
                            icon = R.drawable.ic_save,
                            title = stringResource(R.string.playlist_empty_title),
                            message = stringResource(R.string.playlist_empty_message),
                        )
                    }
                }
                items(current.playlist.videos, key = { it.id }) { video ->
                    // La croix a sa propre place à droite : elle ne recouvre jamais le titre.
                    Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                        Box(modifier = Modifier.weight(1f)) {
                            SuggestedVideoCard(video = video, modifier = Modifier.clickable { onVideoClick(video.id) })
                        }
                        IconButton(onClick = { viewModel.removeVideo(video.id) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.playlist_remove_video),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// En-tête de la référence : grande miniature de la première vidéo, titre, « Playlist · Privée ·
// N vidéos », puis Renommer et Supprimer en boutons ronds à icône.
@Composable
private fun PlaylistHeader(playlist: PlaylistDetail, onRename: () -> Unit, onDelete: () -> Unit) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val thumbnail = playlist.videos.firstOrNull()?.thumbnailUrl
            if (thumbnail != null) {
                AsyncImage(
                    model = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_save),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        Text(
            text = playlist.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(
                R.string.playlist_meta,
                pluralStringResource(R.plurals.playlist_video_count, playlist.videos.size, playlist.videos.size),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalIconButton(onClick = onRename) {
                Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.playlist_rename), modifier = Modifier.size(20.dp))
            }
            FilledTonalIconButton(onClick = onDelete) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.playlist_delete), modifier = Modifier.size(20.dp))
            }
        }
    }
}
