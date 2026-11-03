package com.youtubeclone.app.presentation.watch

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.youtubeclone.app.R

/**
 * Menu du lecteur (ADR-017) : qualité, parmi celles réellement disponibles pour la vidéo, et
 * lecture automatique, le même réglage que Vous → Paramètres.
 * [selectedQuality] null : « Auto », le lecteur choisit selon le débit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerMenuSheet(
    qualities: List<Int>,
    selectedQuality: Int?,
    onQualitySelected: (Int?) -> Unit,
    autoplayEnabled: Boolean?,
    onAutoplayChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            SectionTitle(stringResource(R.string.player_menu_quality))
            // Une seule qualité (MP4, ou source en basse définition) : rien à choisir.
            if (qualities.size <= 1) {
                Text(
                    text = qualities.firstOrNull()?.let { stringResource(R.string.player_menu_single_quality, it) }
                        ?: stringResource(R.string.player_menu_quality_unknown),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            } else {
                QualityRow(stringResource(R.string.player_menu_quality_auto), selectedQuality == null) { onQualitySelected(null) }
                qualities.forEach { size ->
                    QualityRow(stringResource(R.string.player_menu_quality_value, size), selectedQuality == size) {
                        onQualitySelected(size)
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            if (autoplayEnabled != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = autoplayEnabled, role = Role.Switch, onValueChange = onAutoplayChange)
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_autoplay),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = autoplayEnabled, onCheckedChange = null)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun QualityRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
    }
}
