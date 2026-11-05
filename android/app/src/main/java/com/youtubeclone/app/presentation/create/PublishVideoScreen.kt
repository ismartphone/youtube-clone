package com.youtubeclone.app.presentation.create

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.youtubeclone.app.R
import com.youtubeclone.app.presentation.theme.redTextButtonColors
import com.youtubeclone.app.presentation.theme.redButtonColors
import com.youtubeclone.app.domain.model.VideoFormat
import com.youtubeclone.app.domain.model.VideoVisibility
import com.youtubeclone.app.presentation.common.BackTopBar
import com.youtubeclone.app.presentation.common.asText
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Publication d'une vidéo choisie dans la galerie : aperçu, titre, description, catégorie, puis
 * envoi avec sa progression. [onClose] annule un envoi en cours ; [onPublished] ferme l'écran.
 */
@Composable
fun PublishVideoScreen(
    onClose: () -> Unit,
    onPublished: () -> Unit,
    viewModel: PublishVideoViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isPublished) {
        if (state.isPublished) onPublished()
    }
    PublishVideoContent(
        state = state,
        onClose = onClose,
        onTitleChange = viewModel::onTitleChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onCategorySelected = viewModel::onCategorySelected,
        onRetryCategories = viewModel::loadCategories,
        onVisibilityChange = viewModel::onVisibilityChange,
        onCommentsEnabledChange = viewModel::onCommentsEnabledChange,
        onPublish = viewModel::publish,
        onCancel = viewModel::cancel,
    )
}

@Composable
private fun PublishVideoContent(
    state: PublishVideoUiState,
    onClose: () -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onCategorySelected: (String) -> Unit,
    onRetryCategories: () -> Unit,
    onVisibilityChange: (VideoVisibility) -> Unit,
    onCommentsEnabledChange: (Boolean) -> Unit,
    onPublish: () -> Unit,
    onCancel: () -> Unit,
) {
    val editable = !state.isUploading
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        BackTopBar(
            title = stringResource(
                if (state.format == VideoFormat.SHORT) R.string.create_short_title else R.string.create_video_title,
            ),
            onBack = onClose,
        )
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            VideoPreview(uri = state.uri, format = state.format)
            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChange,
                label = { Text(stringResource(R.string.publish_title_label)) },
                supportingText = { Text("${state.title.length} / $TITLE_MAX_LENGTH") },
                singleLine = true,
                enabled = editable,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = onDescriptionChange,
                label = { Text(stringResource(R.string.publish_description_label)) },
                supportingText = { Text("${state.description.length} / $DESCRIPTION_MAX_LENGTH") },
                minLines = 3,
                enabled = editable,
                modifier = Modifier.fillMaxWidth(),
            )
            CategoryField(state = state, enabled = editable, onCategorySelected = onCategorySelected)
            if (state.categoriesFailed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.publish_categories_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(colors = redTextButtonColors(), onClick = onRetryCategories) { Text(stringResource(R.string.retry)) }
                }
            }
            PublishSettings(
                visibility = state.visibility,
                commentsEnabled = state.commentsEnabled,
                enabled = editable,
                onVisibilityChange = onVisibilityChange,
                onCommentsEnabledChange = onCommentsEnabledChange,
            )
            state.message?.let { message ->
                Text(
                    text = message.asText(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (state.isUploading) {
                UploadProgressRow(progress = state.progress, onCancel = onCancel)
            } else {
                Button(
                    onClick = onPublish,
                    colors = redButtonColors(),
                    enabled = state.canPublish,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.publish_submit))
                }
            }
        }
    }
}

// Aperçu : une image tirée de la vidéo, au format de l'onglet (paysage ou portrait).
@Composable
private fun VideoPreview(uri: String, format: VideoFormat) {
    val context = LocalContext.current
    val frame by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) { firstFrame(context, uri) }
    }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val modifier = if (format == VideoFormat.SHORT) {
            Modifier
                .height(PREVIEW_SHORT_HEIGHT)
                .aspectRatio(9f / 16f)
        } else {
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        }
        Box(
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            frame?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = stringResource(R.string.publish_preview),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private val PREVIEW_SHORT_HEIGHT = 280.dp

private fun firstFrame(context: Context, uri: String): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri.toUri())
        retriever.getFrameAtTime(0)
    } catch (_: RuntimeException) {
        null
    } finally {
        retriever.release()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryField(
    state: PublishVideoUiState,
    enabled: Boolean,
    onCategorySelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = state.categories.firstOrNull { it.id == state.categoryId }?.name.orEmpty()
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.publish_category_label)) },
            placeholder = { Text(stringResource(R.string.publish_category_placeholder)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = {
                        onCategorySelected(category.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun UploadProgressRow(progress: Float?, onCancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (progress != null) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        // Fichier entièrement envoyé : le serveur l'analyse et prépare la miniature.
        Text(
            text = when {
                progress == null -> stringResource(R.string.publish_sending)
                progress >= 1f -> stringResource(R.string.publish_processing)
                else -> stringResource(R.string.publish_progress, (progress * 100).roundToInt())
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.publish_cancel))
        }
    }
}

// Réglages de publication (ADR-016), repliés sous le formulaire : la ligne résume l'état, et se
// déplie sur la visibilité et les commentaires.
@Composable
private fun PublishSettings(
    visibility: VideoVisibility,
    commentsEnabled: Boolean,
    enabled: Boolean,
    onVisibilityChange: (VideoVisibility) -> Unit,
    onCommentsEnabledChange: (Boolean) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val chevron by animateFloatAsState(if (expanded) 90f else 0f, label = "chevron")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { expanded = !expanded }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.publish_settings),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(visibilityLabel(visibility)) + " · " + stringResource(
                        if (commentsEnabled) R.string.publish_settings_comments_on else R.string.publish_settings_comments_off,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                modifier = Modifier.rotate(chevron),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                Text(
                    text = stringResource(R.string.publish_settings_visibility),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                VideoVisibility.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == visibility,
                                enabled = enabled,
                                role = Role.RadioButton,
                                onClick = { onVisibilityChange(option) },
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == visibility, onClick = null, enabled = enabled)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(stringResource(visibilityLabel(option)), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = stringResource(visibilityDetail(option)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = commentsEnabled,
                            enabled = enabled,
                            role = Role.Switch,
                            onValueChange = onCommentsEnabledChange,
                        )
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.publish_settings_comments),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = commentsEnabled, onCheckedChange = null, enabled = enabled)
                }
                Text(
                    text = stringResource(R.string.publish_settings_later),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

private fun visibilityLabel(visibility: VideoVisibility) = when (visibility) {
    VideoVisibility.PUBLIC -> R.string.visibility_public
    VideoVisibility.UNLISTED -> R.string.visibility_unlisted
    VideoVisibility.PRIVATE -> R.string.visibility_private
}

private fun visibilityDetail(visibility: VideoVisibility) = when (visibility) {
    VideoVisibility.PUBLIC -> R.string.visibility_public_detail
    VideoVisibility.UNLISTED -> R.string.visibility_unlisted_detail
    VideoVisibility.PRIVATE -> R.string.visibility_private_detail
}
