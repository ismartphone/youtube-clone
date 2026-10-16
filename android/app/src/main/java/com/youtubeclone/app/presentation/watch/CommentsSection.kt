package com.youtubeclone.app.presentation.watch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.Comment
import com.youtubeclone.app.domain.model.CommentAuthor
import com.youtubeclone.app.presentation.common.asText
import com.youtubeclone.app.presentation.common.formatRelativeDate
import com.youtubeclone.app.presentation.theme.LocalReduceMotion
import com.youtubeclone.app.presentation.theme.Motion

/**
 * Commentaires sous le lecteur : nombre, champ de saisie, puis la liste.
 * Rendue dans la colonne défilante de l'écran de lecture, d'où une liste simple et un bouton
 * « Afficher plus » plutôt qu'une LazyColumn imbriquée.
 */
@Composable
fun CommentsSection(
    state: CommentsUiState,
    isLoggedIn: Boolean,
    onDraftChange: (String) -> Unit,
    onPost: () -> Unit,
    onLoginRequired: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.comments_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (!state.isLoading && state.error == null) {
                Text(
                    text = state.total.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (isLoggedIn) {
            CommentComposer(state, onDraftChange, onPost)
        } else {
            // Visiteur : le champ invite à se connecter.
            Text(
                text = stringResource(R.string.comments_login_to_comment),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onLoginRequired)
                    .padding(16.dp),
            )
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            state.error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.error.asText(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
            state.comments.isEmpty() -> Text(
                text = stringResource(R.string.comments_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                state.comments.forEach { comment ->
                    // key : chaque commentaire garde son état d'apparition quand la liste change.
                    key(comment.id) {
                        AnimatedCommentItem(comment, animateIn = comment.id in state.arrivedCommentIds)
                    }
                }
                if (state.hasMore) {
                    TextButton(onClick = onLoadMore, enabled = !state.isLoadingMore) {
                        Text(stringResource(R.string.comments_load_more))
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentComposer(
    state: CommentsUiState,
    onDraftChange: (String) -> Unit,
    onPost: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.End) {
        OutlinedTextField(
            value = state.draft,
            onValueChange = onDraftChange,
            placeholder = { Text(stringResource(R.string.comments_placeholder)) },
            enabled = !state.isPosting,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = onPost, enabled = state.canPost) {
            Text(stringResource(R.string.comments_post))
        }
    }
}

@Composable
private fun CommentItem(comment: Comment) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AuthorAvatar(comment.author)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "${comment.author.displayName} · ${formatRelativeDate(comment.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = comment.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

// Photo de l'auteur, ou à défaut l'initiale de son nom.
@Composable
internal fun AuthorAvatar(author: CommentAuthor, size: Dp = 32.dp) {
    val modifier = Modifier
        .size(size)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceVariant)
    if (author.avatarUrl != null) {
        AsyncImage(
            model = author.avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = author.displayName.take(1).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

// Un commentaire arrivé pendant qu'on regarde glisse depuis le haut en s'ouvrant, au lieu
// d'apparaître d'un coup. Ceux de la liste chargée au départ s'affichent directement.
@Composable
private fun AnimatedCommentItem(comment: Comment, animateIn: Boolean) {
    val reduceMotion = LocalReduceMotion.current
    val visibility = remember { MutableTransitionState(!animateIn).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = visibility,
        enter = if (reduceMotion) {
            fadeIn(tween(Motion.REDUCED_FADE_MS))
        } else {
            expandVertically(tween(Motion.FADE_MS, easing = Motion.Easing), expandFrom = Alignment.Top) +
                slideInVertically(tween(Motion.FADE_MS, easing = Motion.Easing)) { height -> -height / 2 } +
                fadeIn(tween(Motion.FADE_MS))
        },
    ) {
        CommentItem(comment)
    }
}
