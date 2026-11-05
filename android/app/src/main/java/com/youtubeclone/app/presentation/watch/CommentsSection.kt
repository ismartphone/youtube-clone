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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import coil3.compose.AsyncImage
import com.youtubeclone.app.R
import com.youtubeclone.app.presentation.theme.redTextButtonColors
import com.youtubeclone.app.domain.model.Channel
import com.youtubeclone.app.domain.model.Comment
import com.youtubeclone.app.domain.model.CommentAuthor
import com.youtubeclone.app.domain.model.CommentReaction
import com.youtubeclone.app.domain.model.CommentSort
import com.youtubeclone.app.presentation.common.ChannelAvatar
import com.youtubeclone.app.presentation.common.asText
import com.youtubeclone.app.presentation.common.formatCompactCount
import com.youtubeclone.app.presentation.common.MinTouchTarget
import com.youtubeclone.app.presentation.common.minTouchTarget
import com.youtubeclone.app.presentation.common.formatRelativeDate
import com.youtubeclone.app.presentation.theme.LocalReduceMotion
import com.youtubeclone.app.presentation.theme.Motion

// Rouge du cœur du créateur, comme sur YouTube.
private val HeartRed = Color(0xFFFF0033)

/** Actions de la section, branchées sur le [CommentsController]. */
class CommentsActions(
    val onDraftChange: (String) -> Unit,
    val onPost: () -> Unit,
    val onLoadMore: () -> Unit,
    val onRetry: () -> Unit,
    val onSortChange: (CommentSort) -> Unit,
    val onToggleReplies: (Comment) -> Unit,
    val onLoadMoreReplies: (threadId: String) -> Unit,
    val onReply: (Comment) -> Unit,
    val onCancelReply: () -> Unit,
    val onReplyDraftChange: (String) -> Unit,
    val onPostReply: () -> Unit,
    val onReact: (Comment, CommentReaction) -> Unit,
    val onToggleHeart: (Comment) -> Unit,
)

/**
 * Commentaires sous le lecteur : nombre, tri, champ de saisie, puis la liste, avec sous chaque
 * commentaire ses avis, « Répondre » et ses réponses (ADR-023).
 * Rendue dans une colonne défilante, d'où une liste simple et des boutons « Afficher plus »
 * plutôt qu'une LazyColumn imbriquée. Un visiteur qui agit est invité à se connecter.
 */
@Composable
fun CommentsSection(
    state: CommentsUiState,
    isLoggedIn: Boolean,
    actions: CommentsActions,
    onLoginRequired: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Les actions réservées aux comptes renvoient le visiteur vers la connexion.
    val requireLogin = { action: () -> Unit -> if (isLoggedIn) action() else onLoginRequired() }
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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CommentSort.entries.forEach { sort ->
                FilterChip(
                    selected = state.sort == sort,
                    onClick = { actions.onSortChange(sort) },
                    label = {
                        Text(stringResource(if (sort == CommentSort.Top) R.string.comments_sort_top else R.string.comments_sort_newest))
                    },
                    // Puce choisie en blanc, comme les catégories du fil.
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.onBackground,
                        selectedLabelColor = MaterialTheme.colorScheme.background,
                    ),
                )
            }
        }

        if (isLoggedIn) {
            CommentComposer(
                value = state.draft,
                placeholder = stringResource(R.string.comments_placeholder),
                enabled = !state.isPosting,
                canSend = state.canPost,
                sendLabel = stringResource(R.string.comments_post),
                onValueChange = actions.onDraftChange,
                onSend = actions.onPost,
            )
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
                TextButton(colors = redTextButtonColors(), onClick = actions.onRetry) { Text(stringResource(R.string.retry)) }
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
                        AnimatedEntry(animateIn = comment.id in state.arrivedCommentIds) {
                            CommentThread(comment, state, actions, requireLogin)
                        }
                    }
                }
                if (state.hasMore) {
                    TextButton(colors = redTextButtonColors(), onClick = actions.onLoadMore, enabled = !state.isLoadingMore) {
                        Text(stringResource(R.string.comments_load_more))
                    }
                }
            }
        }
    }
}

// Un commentaire, puis « N réponses », le fil déplié et le champ de réponse s'il est ouvert ici.
@Composable
private fun CommentThread(
    comment: Comment,
    state: CommentsUiState,
    actions: CommentsActions,
    requireLogin: (() -> Unit) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CommentItem(comment, state, actions, requireLogin, avatarSize = 32.dp)
        val thread = state.replies[comment.id]
        // Repère du fil : les réponses s'alignent sur le texte du commentaire.
        Column(modifier = Modifier.padding(start = 44.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (comment.replyCount > 0) {
                val expanded = thread?.expanded == true
                Row(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .minTouchTarget()
                        .clickable(role = Role.Button) { actions.onToggleReplies(comment) }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_expand_more),
                        // Décoratif (ADR-030) : le sens est porté par le texte voisin, TalkBack ignore l'image.
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp).rotate(if (expanded) 180f else 0f),
                    )
                    Text(
                        text = pluralStringResource(R.plurals.comments_reply_count, comment.replyCount, comment.replyCount),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (thread?.expanded == true) {
                thread.items.forEach { reply ->
                    key(reply.id) {
                        AnimatedEntry(animateIn = reply.id in state.arrivedCommentIds) {
                            CommentItem(reply, state, actions, requireLogin, avatarSize = 24.dp)
                        }
                    }
                }
                when {
                    thread.isLoading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    thread.hasMore -> TextButton(colors = redTextButtonColors(), onClick = { actions.onLoadMoreReplies(comment.id) }) {
                        Text(stringResource(R.string.comments_more_replies))
                    }
                }
            }
            if (state.replyTarget?.threadId == comment.id) {
                CommentComposer(
                    value = state.replyDraft,
                    placeholder = stringResource(R.string.comments_reply_placeholder),
                    enabled = !state.isPostingReply,
                    canSend = state.canPostReply,
                    sendLabel = stringResource(R.string.comments_reply),
                    onValueChange = actions.onReplyDraftChange,
                    onSend = actions.onPostReply,
                    onCancel = actions.onCancelReply,
                )
            }
        }
    }
}

@Composable
private fun CommentItem(
    comment: Comment,
    state: CommentsUiState,
    actions: CommentsActions,
    requireLogin: (() -> Unit) -> Unit,
    avatarSize: Dp,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AuthorAvatar(comment.author, size = avatarSize)
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
            CommentActionsRow(comment, state, actions, requireLogin)
        }
    }
}

// Pouce levé et son nombre, pouce baissé sans nombre, cœur du créateur, « Répondre ».
@Composable
private fun CommentActionsRow(
    comment: Comment,
    state: CommentsUiState,
    actions: CommentsActions,
    requireLogin: (() -> Unit) -> Unit,
) {
    val pending = comment.id in state.pendingIds
    val activeColor = MaterialTheme.colorScheme.primary
    val idleColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { requireLogin { actions.onReact(comment, CommentReaction.Like) } },
            enabled = !pending,
            modifier = Modifier.size(MinTouchTarget),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_like),
                contentDescription = stringResource(R.string.comments_like),
                tint = if (comment.reaction == CommentReaction.Like) activeColor else idleColor,
                modifier = Modifier.size(18.dp),
            )
        }
        if (comment.likeCount > 0) {
            Text(
                text = formatCompactCount(comment.likeCount),
                style = MaterialTheme.typography.bodySmall,
                color = idleColor,
            )
        }
        IconButton(
            onClick = { requireLogin { actions.onReact(comment, CommentReaction.Dislike) } },
            enabled = !pending,
            modifier = Modifier.size(MinTouchTarget),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_dislike),
                contentDescription = stringResource(R.string.comments_dislike),
                tint = if (comment.reaction == CommentReaction.Dislike) activeColor else idleColor,
                modifier = Modifier.size(18.dp),
            )
        }
        val creator = state.creator
        when {
            // Cœur posé : l'avatar du créateur avec un petit cœur ; le créateur le retire d'un appui.
            comment.heartedByCreator && creator != null -> CreatorHeart(
                creator = creator,
                onClick = if (state.canHeart && !pending) ({ actions.onToggleHeart(comment) }) else null,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            state.canHeart -> IconButton(
                onClick = { actions.onToggleHeart(comment) },
                enabled = !pending,
                modifier = Modifier.size(MinTouchTarget),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_heart_outline),
                    contentDescription = stringResource(R.string.comments_heart_add),
                    tint = idleColor,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        TextButton(colors = redTextButtonColors(), onClick = { requireLogin { actions.onReply(comment) } }) {
            Text(
                text = stringResource(R.string.comments_reply),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

// Avatar du créateur, avec un petit cœur rouge en bas à droite, comme sur YouTube.
@Composable
private fun CreatorHeart(creator: Channel, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val description = if (onClick != null) {
        stringResource(R.string.comments_heart_remove)
    } else {
        stringResource(R.string.comments_hearted_by, creator.name)
    }
    Box(
        modifier = modifier
            .size(if (onClick != null) MinTouchTarget else 28.dp)
            .then(if (onClick != null) Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        ChannelAvatar(channel = creator, modifier = Modifier.size(20.dp).clip(CircleShape))
        Icon(
            painter = painterResource(R.drawable.ic_heart),
            contentDescription = description,
            tint = HeartRed,
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp).size(12.dp),
        )
    }
}

@Composable
private fun CommentComposer(
    value: String,
    placeholder: String,
    enabled: Boolean,
    canSend: Boolean,
    sendLabel: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    // Curseur après le texte prérempli (la mention d'une réponse) ; un texte remis à zéro de
    // l'extérieur, après l'envoi, remplace le champ.
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    // Le champ de réponse s'ouvre sous le fil : il prend le focus, le clavier apparaît.
    val focusRequester = remember { FocusRequester() }
    if (onCancel != null) LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Column(horizontalAlignment = Alignment.End) {
        OutlinedTextField(
            value = field,
            onValueChange = {
                field = it
                onValueChange(it.text)
            },
            placeholder = { Text(placeholder) },
            enabled = enabled,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        )
        Row {
            if (onCancel != null) {
                TextButton(colors = redTextButtonColors(), onClick = onCancel) { Text(stringResource(R.string.comments_reply_cancel)) }
            }
            TextButton(colors = redTextButtonColors(), onClick = onSend, enabled = canSend) { Text(sendLabel) }
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
            // Décoratif (ADR-030) : le sens est porté par le texte voisin, TalkBack ignore l'image.
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        // L'initiale est décorative : le nom de l'auteur est lu avec le commentaire.
        Box(modifier = modifier.clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
            Text(
                text = author.displayName.take(1).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

// Un commentaire ou une réponse arrivé pendant qu'on regarde glisse depuis le haut en s'ouvrant,
// au lieu d'apparaître d'un coup. Ceux de la liste chargée au départ s'affichent directement.
@Composable
private fun AnimatedEntry(animateIn: Boolean, content: @Composable () -> Unit) {
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
        content()
    }
}
