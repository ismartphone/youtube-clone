package com.youtubeclone.app.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.VideoVisibility

/**
 * Pastille d'état d'une vidéo non publique (ADR-016). Seul le propriétaire reçoit ces vidéos,
 * sur la page de sa chaîne : rien n'est affiché pour une vidéo publique.
 */
@Composable
fun VisibilityBadge(visibility: VideoVisibility, modifier: Modifier = Modifier) {
    val label = when (visibility) {
        VideoVisibility.PUBLIC -> return
        VideoVisibility.UNLISTED -> R.string.visibility_unlisted
        VideoVisibility.PRIVATE -> R.string.visibility_private
    }
    Text(
        text = stringResource(label),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = Color.White,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
