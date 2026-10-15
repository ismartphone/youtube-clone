package com.youtubeclone.app.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.youtubeclone.app.R

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(uiState = uiState, onRetry = viewModel::load, modifier = modifier)
}

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        HomeUiState.Loading -> CenteredBox(modifier) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }

        HomeUiState.Empty -> CenteredBox(modifier) {
            MessageText(stringResource(R.string.home_empty))
        }

        is HomeUiState.Error -> CenteredBox(modifier) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MessageText(
                    if (uiState.code != null) {
                        stringResource(uiState.messageRes, uiState.code)
                    } else {
                        stringResource(uiState.messageRes)
                    },
                )
                Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
        }

        is HomeUiState.Success -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(uiState.videos, key = { it.id }) { video ->
                VideoCard(video = video)
            }
        }
    }
}

@Composable
private fun CenteredBox(modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun MessageText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
