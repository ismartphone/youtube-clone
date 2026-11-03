package com.youtubeclone.app.presentation.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.HANDLE_MAX_LENGTH
import com.youtubeclone.app.domain.model.HANDLE_MIN_LENGTH
import com.youtubeclone.app.presentation.common.BackTopBar
import com.youtubeclone.app.presentation.common.asText

/**
 * Création de la chaîne de l'utilisateur : nom, identifiant (@handle) proposé à partir du nom,
 * description facultative. [onCreated] ouvre la page de la nouvelle chaîne.
 */
@Composable
fun CreateChannelScreen(
    onClose: () -> Unit,
    onCreated: (channelId: String) -> Unit,
    viewModel: CreateChannelViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.createdChannelId) {
        state.createdChannelId?.let(onCreated)
    }
    val editable = !state.isSaving
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        BackTopBar(title = stringResource(R.string.create_channel_title), onBack = onClose)
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.create_channel_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.create_channel_name)) },
                supportingText = { Text("${state.name.length} / $CHANNEL_NAME_MAX_LENGTH") },
                singleLine = true,
                enabled = editable,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            val handleError = state.handleError
            OutlinedTextField(
                value = state.handle,
                onValueChange = viewModel::onHandleChange,
                label = { Text(stringResource(R.string.create_channel_handle)) },
                prefix = { Text("@") },
                isError = handleError != null || state.isHandleMalformed,
                supportingText = {
                    Text(
                        when {
                            handleError != null -> handleError.asText()
                            else -> stringResource(R.string.create_channel_handle_rule, HANDLE_MIN_LENGTH, HANDLE_MAX_LENGTH)
                        },
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
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text(stringResource(R.string.create_channel_description)) },
                supportingText = { Text("${state.description.length} / $CHANNEL_DESCRIPTION_MAX_LENGTH") },
                minLines = 3,
                enabled = editable,
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let {
                Text(
                    text = it.asText(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                onClick = viewModel::create,
                enabled = state.canCreate,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.create_channel_submit))
                }
            }
        }
    }
}
