package com.youtubeclone.app.presentation.channel

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.youtubeclone.app.R
import com.youtubeclone.app.presentation.theme.redTextButtonColors
import com.youtubeclone.app.presentation.theme.redButtonColors
import com.youtubeclone.app.domain.model.ChannelImageKind
import com.youtubeclone.app.domain.model.HANDLE_MAX_LENGTH
import com.youtubeclone.app.domain.model.HANDLE_MIN_LENGTH
import com.youtubeclone.app.presentation.common.BackTopBar
import com.youtubeclone.app.presentation.common.ChannelAvatar
import com.youtubeclone.app.presentation.common.ErrorState
import com.youtubeclone.app.presentation.common.ErrorToast
import com.youtubeclone.app.presentation.common.LoadingState
import com.youtubeclone.app.presentation.common.asText

/**
 * Personnalisation de sa chaîne (ADR-018) : bannière, photo de profil, nom, handle et description.
 * Une image choisie est envoyée aussitôt, et le serveur la recadre ; les textes sont enregistrés
 * avec le bouton du bas.
 */
@Composable
fun CustomizeChannelScreen(
    onClose: () -> Unit,
    viewModel: CustomizeChannelViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ErrorToast(viewModel.messages)
    // Sélecteur de média Android : aucune permission de stockage à demander.
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onImagePicked(ChannelImageKind.Photo, uri?.toString())
    }
    val pickBanner = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onImagePicked(ChannelImageKind.Banner, uri?.toString())
    }
    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        BackTopBar(title = stringResource(R.string.customize_channel_title), onBack = onClose)
        when (val current = state) {
            CustomizeChannelUiState.Loading -> LoadingState(Modifier.weight(1f))
            is CustomizeChannelUiState.Error -> ErrorState(current.error, viewModel::load, Modifier.weight(1f))
            is CustomizeChannelUiState.Ready -> {
                val form = current.form
                val editable = !form.isSaving
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SectionTitle(stringResource(R.string.customize_channel_banner))
                    BannerPreview(form.profile.bannerUrl, isBusy = form.imageBusy == ChannelImageKind.Banner)
                    ImageActions(
                        changeLabel = stringResource(
                            if (form.profile.bannerUrl != null) R.string.customize_channel_change else R.string.customize_channel_add,
                        ),
                        onChange = { pickBanner.launch(imageOnly) },
                        canRemove = form.profile.bannerUrl != null,
                        onRemove = { viewModel.onRemoveImage(ChannelImageKind.Banner) },
                        enabled = form.imageBusy == null,
                    )

                    SectionTitle(stringResource(R.string.customize_channel_photo))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            ChannelAvatar(
                                channel = form.profile.channel,
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(CircleShape),
                                initialStyle = MaterialTheme.typography.headlineMedium,
                            )
                            if (form.imageBusy == ChannelImageKind.Photo) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            }
                        }
                        ImageActions(
                            changeLabel = stringResource(R.string.customize_channel_change),
                            onChange = { pickPhoto.launch(imageOnly) },
                            // Sans photo choisie, l'avatar est généré à partir du nom : rien à retirer.
                            canRemove = form.profile.hasPhoto,
                            onRemove = { viewModel.onRemoveImage(ChannelImageKind.Photo) },
                            enabled = form.imageBusy == null,
                        )
                    }

                    OutlinedTextField(
                        value = form.name,
                        onValueChange = viewModel::onNameChange,
                        label = { Text(stringResource(R.string.create_channel_name)) },
                        supportingText = { Text("${form.name.length} / $CHANNEL_NAME_MAX_LENGTH") },
                        singleLine = true,
                        enabled = editable,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val handleError = form.handleError
                    OutlinedTextField(
                        value = form.handle,
                        onValueChange = viewModel::onHandleChange,
                        label = { Text(stringResource(R.string.create_channel_handle)) },
                        prefix = { Text("@") },
                        isError = handleError != null || form.isHandleMalformed,
                        supportingText = {
                            Text(
                                handleError?.asText()
                                    ?: stringResource(R.string.create_channel_handle_rule, HANDLE_MIN_LENGTH, HANDLE_MAX_LENGTH),
                            )
                        },
                        singleLine = true,
                        enabled = editable,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Ascii,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.description,
                        onValueChange = viewModel::onDescriptionChange,
                        label = { Text(stringResource(R.string.create_channel_description)) },
                        supportingText = { Text("${form.description.length} / $CHANNEL_DESCRIPTION_MAX_LENGTH") },
                        minLines = 3,
                        enabled = editable,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    form.error?.let {
                        Text(
                            text = it.asText(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Button(
                        onClick = viewModel::save,
                        colors = redButtonColors(),
                        enabled = form.canSave,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (form.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.customize_channel_save))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

// Aperçu au format de la page de chaîne. Sans bannière, un aplat sobre tient la place.
@Composable
private fun BannerPreview(bannerUrl: String?, isBusy: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(BANNER_RATIO)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bannerUrl != null) {
            AsyncImage(
                model = bannerUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        if (isBusy) CircularProgressIndicator(modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun ImageActions(
    changeLabel: String,
    onChange: () -> Unit,
    canRemove: Boolean,
    onRemove: () -> Unit,
    enabled: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onChange, enabled = enabled) { Text(changeLabel) }
        if (canRemove) {
            TextButton(colors = redTextButtonColors(), onClick = onRemove, enabled = enabled) { Text(stringResource(R.string.customize_channel_remove)) }
        }
    }
}

private const val BANNER_RATIO = 3.6f
