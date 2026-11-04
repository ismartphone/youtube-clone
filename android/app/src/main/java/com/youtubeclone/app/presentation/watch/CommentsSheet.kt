package com.youtubeclone.app.presentation.watch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Liste complète des commentaires d'une vidéo, dans une feuille du bas (Material).
 * Partagée par l'écran de lecture et l'onglet Shorts.
 * [onLoginRequired] est appelé après la fermeture de la feuille.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommentsSheet(
    state: CommentsUiState,
    controller: CommentsController,
    isLoggedIn: Boolean,
    onLoginRequired: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            CommentsSection(
                state = state,
                isLoggedIn = isLoggedIn,
                actions = remember(controller) {
                    CommentsActions(
                        onDraftChange = controller::onDraftChange,
                        onPost = controller::post,
                        onLoadMore = controller::loadMore,
                        onRetry = controller::loadFirstPage,
                        onSortChange = controller::setSort,
                        onToggleReplies = controller::toggleReplies,
                        onLoadMoreReplies = controller::loadMoreReplies,
                        onReply = controller::startReply,
                        onCancelReply = controller::cancelReply,
                        onReplyDraftChange = controller::onReplyDraftChange,
                        onPostReply = controller::postReply,
                        onReact = controller::react,
                        onToggleHeart = controller::toggleHeart,
                    )
                },
                onLoginRequired = {
                    onDismiss()
                    onLoginRequired()
                },
            )
        }
    }
}
