package com.youtubeclone.app.presentation.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.youtubeclone.app.R
import com.youtubeclone.app.presentation.common.asText


/** Modification des centres d'intérêt, depuis l'onglet « Vous ». */
@Composable
fun InterestsScreen(
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InterestsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onSaved()
    }
    InterestsContent(
        state = state,
        title = R.string.interests_title,
        message = R.string.interests_message,
        submitLabel = R.string.interests_save,
        onToggle = viewModel::toggle,
        onRetry = viewModel::loadCategories,
        onSubmit = viewModel::save,
        modifier = modifier,
    )
}

@Composable
private fun InterestsContent(
    state: InterestsUiState,
    @StringRes title: Int,
    @StringRes message: Int,
    @StringRes submitLabel: Int,
    onToggle: (String) -> Unit,
    onRetry: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CategoryChips(state, onToggle, onRetry)
        }
        // Bouton toujours visible en bas, même avec beaucoup de catégories.
        Button(
            onClick = onSubmit,
            enabled = !state.isSaving,
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text(stringResource(submitLabel))
        }
    }
}

// Catégories en puces à cocher. Si elles ne se chargent pas, le choix reste facultatif :
// on peut réessayer ou continuer sans.
@Composable
private fun CategoryChips(
    state: InterestsUiState,
    onToggle: (String) -> Unit,
    onRetry: () -> Unit,
) {
    when (val categories = state.categories) {
        CategoriesState.Loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        is CategoriesState.Error -> Column {
            Text(
                text = categories.error.asText(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
        is CategoriesState.Loaded -> if (categories.categories.isEmpty()) {
            Text(
                text = stringResource(R.string.interests_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.categories.forEach { category ->
                    FilterChip(
                        selected = category.id in state.selected,
                        onClick = { onToggle(category.id) },
                        label = { Text(category.name) },
                    )
                }
            }
        }
    }
}
