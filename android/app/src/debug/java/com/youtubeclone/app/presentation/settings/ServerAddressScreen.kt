package com.youtubeclone.app.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.youtubeclone.app.R
import com.youtubeclone.app.presentation.common.BackTopBar

/** Vous → Paramètres → Adresse du serveur (ADR-027, debug) : champ, test, retour au défaut, enregistrement. */
@Composable
fun ServerAddressScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServerAddressViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier = modifier.fillMaxSize()) {
        BackTopBar(title = stringResource(R.string.server_address_title), onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.server_address_current, viewModel.currentUrl),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.server_address_default, viewModel.defaultUrl),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.input,
                onValueChange = viewModel::onInputChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.server_address_field)) },
                placeholder = { Text(stringResource(R.string.server_address_hint)) },
                singleLine = true,
                isError = state.invalid,
                supportingText = if (state.invalid) {
                    { Text(stringResource(R.string.server_address_invalid)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                enabled = !state.saving,
            )
            OutlinedButton(
                onClick = viewModel::testConnection,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.test != ConnectionTest.Running && !state.saving,
            ) { Text(stringResource(R.string.server_address_test)) }
            ConnectionTestResult(state.test)
            OutlinedButton(
                onClick = viewModel::useDefault,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.saving,
            ) { Text(stringResource(R.string.server_address_reset)) }
            Text(
                text = stringResource(R.string.server_address_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.saving,
            ) { Text(stringResource(R.string.server_address_save)) }
        }
    }
}

@Composable
private fun ConnectionTestResult(test: ConnectionTest) {
    val (text, isError) = when (test) {
        ConnectionTest.Idle -> return
        ConnectionTest.Running -> stringResource(R.string.server_address_testing) to false
        ConnectionTest.Ok -> stringResource(R.string.server_address_test_ok) to false
        is ConnectionTest.HttpError -> stringResource(R.string.server_address_test_http, test.code) to true
        is ConnectionTest.Failed -> stringResource(R.string.server_address_test_fail) to true
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
    )
}
