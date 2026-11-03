package com.youtubeclone.app.presentation.create

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import coil3.compose.AsyncImage
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.Publication
import com.youtubeclone.app.domain.model.VideoFormat
import com.youtubeclone.app.domain.usecase.ObservePublicationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

@HiltViewModel
class PublishedBannerViewModel @Inject constructor(
    observePublications: ObservePublicationsUseCase,
) : ViewModel() {
    val publications: Flow<Publication> = observePublications()
}

// Durée d'affichage de la confirmation.
private const val BANNER_MS = 4_000L

/**
 * Confirmation d'une publication faite depuis l'application, affichée quelques secondes sur
 * l'écran d'où le créateur était parti, avec la miniature générée par le serveur.
 */
@Composable
fun PublishedBanner(
    modifier: Modifier = Modifier,
    viewModel: PublishedBannerViewModel = hiltViewModel(),
) {
    var current by remember { mutableStateOf<Publication?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.publications.collect {
            current = it
            visible = true
        }
    }
    LaunchedEffect(current) {
        if (current != null) {
            delay(BANNER_MS)
            visible = false
        }
    }
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        current?.let { BannerContent(it) }
    }
}

@Composable
private fun BannerContent(publication: Publication) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (publication) {
            is Publication.VideoPublished -> {
                val video = publication.video
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .height(THUMBNAIL_HEIGHT)
                        .aspectRatio(if (video.format == VideoFormat.SHORT) 9f / 16f else 16f / 9f)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (video.format == VideoFormat.SHORT) R.string.published_short else R.string.published_video,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private val THUMBNAIL_HEIGHT = 56.dp
