package com.youtubeclone.app.presentation.downloads

import android.text.format.Formatter
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.DownloadState
import com.youtubeclone.app.domain.model.DownloadedVideo
import com.youtubeclone.app.domain.model.MAX_DOWNLOADS
import com.youtubeclone.app.presentation.common.BackTopBar
import com.youtubeclone.app.presentation.common.EmptyStateMessage
import com.youtubeclone.app.presentation.common.LoadingState
import com.youtubeclone.app.presentation.common.formatDuration
import java.io.File

/**
 * Téléchargements (ADR-019) : vidéos téléchargées ou en cours, avec leur taille et l'espace
 * occupé ; une vidéo prête se lit hors ligne d'un toucher. Fonctionne sans réseau.
 */
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onPlay: (videoId: String) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = stringResource(R.string.downloads_title), onBack = onBack)
        when (val list = downloads) {
            null -> LoadingState(Modifier.weight(1f))
            else -> if (list.isEmpty()) {
                EmptyStateMessage(
                    icon = R.drawable.ic_download,
                    title = stringResource(R.string.downloads_empty_title),
                    message = stringResource(R.string.downloads_empty_message),
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item(key = "space") {
                        Text(
                            text = stringResource(
                                R.string.downloads_space,
                                list.size,
                                MAX_DOWNLOADS,
                                Formatter.formatShortFileSize(context, list.sumOf { it.sizeBytes }),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    items(list, key = { it.videoId }) { video ->
                        DownloadRow(
                            video = video,
                            onPlay = { onPlay(video.videoId) },
                            onRemove = { viewModel.remove(video.videoId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(video: DownloadedVideo, onPlay: () -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = video.playable, onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(140.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            video.thumbnailPath?.let {
                AsyncImage(
                    model = File(it),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
            Text(
                text = formatDuration(video.durationSeconds),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(video.channelName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val status = when {
                video.state == DownloadState.Failed -> stringResource(R.string.downloads_failed)
                video.state != DownloadState.Completed -> stringResource(R.string.downloads_in_progress, (video.progress * 100).toInt())
                video.needsRevalidation -> stringResource(R.string.downloads_needs_revalidation)
                !video.playable -> stringResource(R.string.downloads_expired)
                else -> Formatter.formatShortFileSize(context, video.sizeBytes)
            }
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = if (video.playable || video.state != DownloadState.Completed) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            if (video.state == DownloadState.Downloading || video.state == DownloadState.Queued) {
                LinearProgressIndicator(
                    progress = { video.progress },
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.downloads_remove),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
