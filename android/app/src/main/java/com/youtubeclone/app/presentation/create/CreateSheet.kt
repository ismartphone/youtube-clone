package com.youtubeclone.app.presentation.create

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.LIVE_MIN_FOLLOWERS
import com.youtubeclone.app.domain.model.MAX_UPLOAD_BYTES
import com.youtubeclone.app.domain.model.PickedVideoProblem
import com.youtubeclone.app.domain.model.SHORT_MAX_SECONDS
import com.youtubeclone.app.domain.model.VideoFormat
import com.youtubeclone.app.presentation.common.ErrorState
import com.youtubeclone.app.presentation.common.LoadingState

/**
 * Onglets de la feuille de création (ADR-015). En direct n'affiche que l'écran de déblocage : la
 * diffusion elle-même n'est pas développée.
 * [format] : format de la vidéo publiée depuis l'onglet, null s'il ne publie pas de vidéo.
 */
enum class CreateTab(
    @StringRes val label: Int,
    @StringRes val title: Int,
    @StringRes val message: Int,
    val format: VideoFormat? = null,
) {
    Video(R.string.create_tab_video, R.string.create_video_title, R.string.create_video_message, VideoFormat.LONG),
    Short(R.string.create_tab_short, R.string.create_short_title, R.string.create_short_message, VideoFormat.SHORT),
    Live(R.string.create_tab_live, R.string.create_live_title, R.string.create_live_message),
    Post(R.string.create_tab_post, R.string.create_post_title, R.string.create_post_message),
}

// Hauteur fixe de la zone de contenu : la feuille ne change pas de taille d'un onglet à l'autre.
private val CONTENT_HEIGHT = 290.dp

/**
 * Feuille ouverte par le bouton + de la barre d'onglets, avec ses onglets en bas : Vidéo, Short,
 * En direct et Post. [onLoginRequired], [onPublishVideo], [onCreateChannel] et [onWritePost] sont appelés après la
 * fermeture de la feuille.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSheet(
    onDismiss: () -> Unit,
    onLoginRequired: () -> Unit,
    onPublishVideo: (uri: String, format: VideoFormat) -> Unit,
    onCreateChannel: () -> Unit,
    onWritePost: () -> Unit,
    viewModel: CreateViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pick by viewModel.pick.collectAsStateWithLifecycle()
    val live by viewModel.live.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(CreateTab.Video) }
    var showLiveRule by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.load() }
    // Onglet En direct : les abonnés sont lus quand on l'ouvre, une fois la chaîne connue.
    LaunchedEffect(selected, state) {
        if (selected == CreateTab.Live && state is CreateUiState.Ready) viewModel.loadLive()
    }
    if (showLiveRule) {
        AlertDialog(
            onDismissRequest = { showLiveRule = false },
            title = { Text(stringResource(R.string.create_live_rule_title)) },
            text = { Text(stringResource(R.string.create_live_rule_message, LIVE_MIN_FOLLOWERS)) },
            confirmButton = {
                TextButton(onClick = { showLiveRule = false }) { Text(stringResource(R.string.create_live_rule_ok)) }
            },
        )
    }
    LaunchedEffect(Unit) {
        viewModel.openPublish.collect { picked ->
            onDismiss()
            onPublishVideo(picked.uri, picked.format)
        }
    }
    // Sélecteur de média Android (photo picker) : aucune permission de stockage à demander.
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        selected.format?.let { format -> viewModel.onVideoPicked(uri?.toString(), format) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CONTENT_HEIGHT),
            ) {
                when (val current = state) {
                    CreateUiState.Loading -> LoadingState()
                    is CreateUiState.Error -> ErrorState(current.error, onRetry = viewModel::load)
                    CreateUiState.LoggedOut -> SheetMessage(
                        title = stringResource(R.string.create_login_title),
                        message = stringResource(R.string.create_login_message),
                        action = stringResource(R.string.login_submit),
                        onAction = {
                            onDismiss()
                            onLoginRequired()
                        },
                    )
                    CreateUiState.NoChannel -> SheetMessage(
                        title = stringResource(R.string.create_no_channel_title),
                        message = stringResource(R.string.create_no_channel_message),
                        action = stringResource(R.string.create_no_channel_action),
                        onAction = {
                            onDismiss()
                            onCreateChannel()
                        },
                    )
                    is CreateUiState.Ready -> if (selected == CreateTab.Live) {
                        LiveUnlock(
                            state = live,
                            onRetry = { viewModel.loadLive(force = true) },
                            onCreateShorts = { selected = CreateTab.Short },
                            onLearnMore = { showLiveRule = true },
                        )
                    } else SheetMessage(
                        title = stringResource(selected.title),
                        message = stringResource(selected.message),
                        action = when {
                            selected.format != null -> stringResource(R.string.create_pick_video)
                            selected == CreateTab.Post -> stringResource(R.string.create_write_post)
                            else -> null
                        },
                        onAction = {
                            if (selected == CreateTab.Post) {
                                onDismiss()
                                onWritePost()
                            } else {
                                pickVideo.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                                )
                            }
                        },
                        isBusy = pick.isChecking,
                        problem = pick.problem?.let { problemText(it) },
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            CreateTabs(
                selected = selected,
                onSelect = {
                    selected = it
                    viewModel.clearPickProblem()
                },
            )
        }
    }
}

@Composable
private fun SheetMessage(
    title: String,
    message: String,
    action: String? = null,
    onAction: () -> Unit = {},
    isBusy: Boolean = false,
    problem: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (isBusy) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        } else if (action != null) {
            Button(onClick = onAction) { Text(action) }
        }
        if (problem != null) {
            Text(
                text = problem,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Écran de déblocage du direct, comme sur YouTube : la condition (50 abonnés), la progression
 * réelle de la chaîne, « Créer des Shorts » pour gagner des abonnés et « En savoir plus ». Seuil
 * atteint : l'écran le constate, sans rien promettre, la diffusion n'étant pas développée (ADR-015).
 */
@Composable
private fun LiveUnlock(
    state: LiveUiState,
    onRetry: () -> Unit,
    onCreateShorts: () -> Unit,
    onLearnMore: () -> Unit,
) {
    when (state) {
        LiveUiState.Loading -> LoadingState()
        is LiveUiState.Error -> ErrorState(state.error, onRetry = onRetry)
        is LiveUiState.Loaded -> {
            val unlocked = state.followerCount >= LIVE_MIN_FOLLOWERS
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                Text(
                    text = if (unlocked) {
                        stringResource(R.string.create_live_unlocked_title, LIVE_MIN_FOLLOWERS)
                    } else {
                        stringResource(R.string.create_live_title)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = if (unlocked) {
                        stringResource(R.string.create_live_unlocked_message)
                    } else {
                        stringResource(R.string.create_live_message, LIVE_MIN_FOLLOWERS)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                // Seuil atteint : la barre pleine suffit, « 73 abonnés sur 50 » n'aurait pas de sens.
                if (!unlocked) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.create_live_progress,
                            state.followerCount,
                            state.followerCount,
                            LIVE_MIN_FOLLOWERS,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                LinearProgressIndicator(
                    progress = { (state.followerCount.toFloat() / LIVE_MIN_FOLLOWERS).coerceAtMost(1f) },
                    modifier = Modifier.fillMaxWidth(),
                    // Rail visible sur le fond de la feuille, qui a la couleur du rail par défaut.
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                )
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!unlocked) {
                        Button(
                            onClick = onCreateShorts,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onBackground,
                                contentColor = MaterialTheme.colorScheme.background,
                            ),
                        ) { Text(stringResource(R.string.create_live_create_shorts)) }
                    }
                    // Pastille grise sur le fond de la feuille, comme la référence.
                    FilledTonalButton(
                        onClick = onLearnMore,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)),
                    ) {
                        Text(stringResource(R.string.create_live_learn_more))
                    }
                }
            }
        }
    }
}

@Composable
private fun problemText(problem: PickedVideoProblem): String = when (problem) {
    PickedVideoProblem.Unreadable -> stringResource(R.string.create_problem_unreadable)
    PickedVideoProblem.TooLarge -> stringResource(R.string.create_problem_too_large, MAX_UPLOAD_BYTES / 1024 / 1024)
    PickedVideoProblem.NotVertical -> stringResource(R.string.create_problem_not_vertical)
    PickedVideoProblem.TooLong -> stringResource(R.string.create_problem_too_long, SHORT_MAX_SECONDS)
}

@Composable
private fun CreateTabs(selected: CreateTab, onSelect: (CreateTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        CreateTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val color = if (isSelected) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            // Onglet actif dans une pastille, comme la référence.
            Column(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = { onSelect(tab) },
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(tab.label),
                    color = color,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
