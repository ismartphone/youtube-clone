package com.youtubeclone.app.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.User
import com.youtubeclone.app.presentation.common.ErrorState
import com.youtubeclone.app.presentation.common.LoadingState

/** Onglet « Vous » : le profil si une session est ouverte, sinon une invitation à se connecter. */
@Composable
fun YouScreen(
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onEditInterests: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: YouViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val current = state) {
        YouUiState.Loading -> LoadingState(modifier)
        YouUiState.LoggedOut -> LoggedOutContent(onLoginClick, onRegisterClick, onEditInterests, modifier)
        is YouUiState.Error -> ErrorState(current.error, onRetry = viewModel::load, modifier = modifier)
        is YouUiState.LoggedIn -> ProfileContent(
            user = current.user,
            onLogoutClick = viewModel::onLogoutClick,
            onEditInterests = onEditInterests,
            modifier = modifier,
        )
    }
}

@Composable
private fun ProfileContent(
    user: User,
    onLogoutClick: () -> Unit,
    onEditInterests: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Avatar(user)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Centres d'intérêt : propres à l'appareil, modifiables aussi sans compte.
        OutlinedButton(onClick = onEditInterests, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.you_edit_interests))
        }
        OutlinedButton(onClick = onLogoutClick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.you_logout))
        }
    }
}

// Photo de profil, ou initiale du nom si l'utilisateur n'en a pas.
@Composable
private fun Avatar(user: User) {
    val avatarModifier = Modifier
        .size(64.dp)
        .clip(CircleShape)
    if (user.avatarUrl != null) {
        AsyncImage(
            model = user.avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = avatarModifier.background(MaterialTheme.colorScheme.surfaceVariant),
        )
    } else {
        Box(
            modifier = avatarModifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = user.displayName.take(1).uppercase(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun LoggedOutContent(
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onEditInterests: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_tab_you),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(72.dp),
        )
        Text(
            text = stringResource(R.string.you_logged_out_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.you_logged_out_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onLoginClick) { Text(stringResource(R.string.login_submit)) }
        TextButton(onClick = onRegisterClick) { Text(stringResource(R.string.you_create_account)) }
        TextButton(onClick = onEditInterests) { Text(stringResource(R.string.you_edit_interests)) }
    }
}
