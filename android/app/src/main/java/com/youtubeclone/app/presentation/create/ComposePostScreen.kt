package com.youtubeclone.app.presentation.create

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.youtubeclone.app.R
import com.youtubeclone.app.presentation.theme.redButtonColors
import com.youtubeclone.app.domain.model.MAX_POST_IMAGE_BYTES
import com.youtubeclone.app.domain.model.POST_TEXT_MAX_LENGTH
import com.youtubeclone.app.domain.model.PostImageProblem
import com.youtubeclone.app.presentation.common.BackTopBar
import com.youtubeclone.app.presentation.common.asText

/**
 * Rédaction d'un post : texte, image facultative avec aperçu, bouton Publier désactivé tant que
 * le texte est vide. [onPublished] ferme l'écran ; la confirmation s'affiche sur l'écran précédent.
 */
@Composable
fun ComposePostScreen(
    onClose: () -> Unit,
    onPublished: () -> Unit,
    viewModel: ComposePostViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isPublished) {
        if (state.isPublished) onPublished()
    }
    // Sélecteur de média Android (photo picker) : aucune permission de stockage à demander.
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onImagePicked(uri?.toString())
    }
    val editable = !state.isSending
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        BackTopBar(title = stringResource(R.string.create_post_title), onBack = onClose)
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.text,
                onValueChange = viewModel::onTextChange,
                placeholder = { Text(stringResource(R.string.compose_post_placeholder)) },
                supportingText = { Text("${state.text.length} / $POST_TEXT_MAX_LENGTH") },
                minLines = 5,
                enabled = editable,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            val imageUri = state.imageUri
            if (imageUri != null) {
                // Aperçu de l'image, avec une croix pour la retirer.
                Box {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = stringResource(R.string.compose_post_image_preview),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = IMAGE_PREVIEW_MAX_HEIGHT)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                    if (editable) {
                        IconButton(
                            onClick = viewModel::removeImage,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.compose_post_remove_image),
                                tint = Color.White,
                            )
                        }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = {
                        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    enabled = editable,
                ) {
                    Text(stringResource(R.string.compose_post_add_image))
                }
            }
            val message = state.imageProblem?.let { imageProblemText(it) } ?: state.error?.asText()
            if (message != null) {
                Text(text = message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = viewModel::publish,
                colors = redButtonColors(),
                enabled = state.canPublish,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.publish_submit))
                }
            }
        }
    }
}

private val IMAGE_PREVIEW_MAX_HEIGHT = 320.dp

@Composable
private fun imageProblemText(problem: PostImageProblem): String = when (problem) {
    PostImageProblem.Unreadable -> stringResource(R.string.compose_post_image_unreadable)
    PostImageProblem.TooLarge -> stringResource(R.string.compose_post_image_too_large, MAX_POST_IMAGE_BYTES / 1024 / 1024)
}
