package com.youtubeclone.app.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.VideoFormat
import com.youtubeclone.app.presentation.common.BackTopBar
import com.youtubeclone.app.presentation.common.ErrorToast
import com.youtubeclone.app.presentation.theme.YoutubeCloneTheme

// Paramètres, ouverts depuis l'onglet « Vous » : lecture automatique, format préféré (ADR-028), partage des statistiques
// d'usage (ADR-013) et, pour un compte connecté, notifications de messages (ADR-025).

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenServerAddress: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val autoplay by viewModel.autoplay.collectAsStateWithLifecycle()
    val onboardingFeedback by viewModel.onboardingFeedback.collectAsStateWithLifecycle()
    val preferredFormat by viewModel.preferredFormat.collectAsStateWithLifecycle()
    val shareUsageStats by viewModel.shareUsageStats.collectAsStateWithLifecycle()
    val messageNotifications by viewModel.messageNotifications.collectAsStateWithLifecycle()
    ErrorToast(viewModel.errors)
    SettingsContent(
        autoplay = autoplay,
        onAutoplayChange = viewModel::onAutoplayChange,
        preferredFormat = preferredFormat,
        onPreferredFormatChange = viewModel::onPreferredFormatChange,
        onboardingFeedback = onboardingFeedback,
        onOnboardingFeedbackChange = viewModel::onOnboardingFeedbackChange,
        shareUsageStats = shareUsageStats,
        onShareUsageStatsChange = viewModel::onShareUsageStatsChange,
        messageNotifications = messageNotifications,
        onMessageNotificationsChange = viewModel::onMessageNotificationsChange,
        onOpenServerAddress = onOpenServerAddress,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun SettingsContent(
    autoplay: Boolean?,
    onAutoplayChange: (Boolean) -> Unit,
    preferredFormat: VideoFormat?,
    onPreferredFormatChange: (VideoFormat) -> Unit,
    onboardingFeedback: Boolean?,
    onOnboardingFeedbackChange: (Boolean) -> Unit,
    shareUsageStats: Boolean?,
    onShareUsageStatsChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    messageNotifications: Boolean? = null,
    onMessageNotificationsChange: (Boolean) -> Unit = {},
    onOpenServerAddress: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Défile : à police très agrandie (ADR-030), les derniers réglages restent atteignables.
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        BackTopBar(title = stringResource(R.string.you_settings), onBack = onBack)
        if (autoplay != null) {
            SwitchRow(
                title = stringResource(R.string.settings_autoplay),
                detail = stringResource(R.string.settings_autoplay_detail),
                checked = autoplay,
                onCheckedChange = onAutoplayChange,
            )
        }
        if (preferredFormat != null) {
            PreferredFormatRow(selected = preferredFormat, onSelect = onPreferredFormatChange)
        }
        if (onboardingFeedback != null) {
            SwitchRow(
                title = stringResource(R.string.settings_onboarding_feedback),
                detail = stringResource(R.string.settings_onboarding_feedback_detail),
                checked = onboardingFeedback,
                onCheckedChange = onOnboardingFeedbackChange,
            )
        }
        if (shareUsageStats != null) {
            SwitchRow(
                title = stringResource(R.string.settings_share_usage_stats),
                detail = stringResource(R.string.settings_share_usage_stats_detail),
                checked = shareUsageStats,
                onCheckedChange = onShareUsageStatsChange,
            )
        }
        // Compte connecté seulement : le réglage est stocké sur le serveur (ADR-025, étape 3).
        if (messageNotifications != null) {
            SwitchRow(
                title = stringResource(R.string.settings_message_notifications),
                detail = stringResource(R.string.settings_message_notifications_detail),
                checked = messageNotifications,
                onCheckedChange = onMessageNotificationsChange,
            )
        }
        // Réglage de test : n'existe qu'en debug (ADR-027), vide en release.
        ServerAddressSettingsRow(onClick = onOpenServerAddress)
    }
}

// Deux choix exclusifs, toute la ligne est cliquable. Prend effet à la prochaine ouverture.
@Composable
private fun PreferredFormatRow(selected: VideoFormat, onSelect: (VideoFormat) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.settings_preferred_format),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.settings_preferred_format_detail),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.selectableGroup()) {
            listOf(
                VideoFormat.LONG to R.string.onboarding_format_videos,
                VideoFormat.SHORT to R.string.onboarding_format_shorts,
            ).forEach { (format, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = selected == format, role = Role.RadioButton, onClick = { onSelect(format) }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected == format, onClick = null)
                    Text(
                        text = stringResource(label),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
                    )
                }
            }
        }
    }
}

// Toute la ligne bascule le réglage, pas seulement l'interrupteur.
@Composable
private fun SwitchRow(title: String, detail: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // onCheckedChange null : le clic est géré par la ligne entière.
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F0F0F)
@Composable
private fun SettingsContentPreview() {
    YoutubeCloneTheme {
        SettingsContent(autoplay = true, onAutoplayChange = {}, preferredFormat = VideoFormat.SHORT, onPreferredFormatChange = {}, onboardingFeedback = true, onOnboardingFeedbackChange = {}, shareUsageStats = true, onShareUsageStatsChange = {}, onBack = {})
    }
}
