package com.youtubeclone.app.presentation.channel

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.youtubeclone.app.R
import com.youtubeclone.app.presentation.theme.redTextButtonColors
import com.youtubeclone.app.domain.model.NotificationLevel
import kotlinx.coroutines.flow.Flow

/** Icône de la cloche pour chaque réglage, comme sur la capture chaine-08. */
@get:DrawableRes
internal val NotificationLevel.icon: Int
    get() = when (this) {
        NotificationLevel.All -> R.drawable.ic_notifications_active
        NotificationLevel.Personalized -> R.drawable.ic_notifications
        NotificationLevel.None -> R.drawable.ic_notifications_off
    }

@get:StringRes
private val NotificationLevel.label: Int
    get() = when (this) {
        NotificationLevel.All -> R.string.notifications_all
        NotificationLevel.Personalized -> R.string.notifications_personalized
        NotificationLevel.None -> R.string.notifications_none
    }

// Chaque réglage dit ce qu'il fait : chez nous, « Personnalisées » veut dire « sans les Shorts ».
@get:StringRes
private val NotificationLevel.caption: Int
    get() = when (this) {
        NotificationLevel.All -> R.string.notifications_all_caption
        NotificationLevel.Personalized -> R.string.notifications_personalized_caption
        NotificationLevel.None -> R.string.notifications_none_caption
    }

/**
 * Feuille Notifications d'un abonnement (ADR-020) : Toutes, Personnalisées, Aucune, puis
 * Se désabonner, comme sur la capture chaine-08.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotificationsSheet(
    selected: NotificationLevel?,
    onSelect: (NotificationLevel) -> Unit,
    onUnsubscribe: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Text(
                text = stringResource(R.string.notifications_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            NotificationLevel.entries.forEach { level ->
                SheetRow(
                    icon = level.icon,
                    label = stringResource(level.label),
                    caption = stringResource(level.caption),
                    selected = level == selected,
                    onClick = { onSelect(level) },
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SheetRow(
                icon = R.drawable.ic_person_remove,
                label = stringResource(R.string.channel_unsubscribe),
                caption = null,
                selected = false,
                onClick = onUnsubscribe,
            )
        }
    }
}

@Composable
private fun SheetRow(@DrawableRes icon: Int, label: String, caption: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Décoratif (ADR-030) : icône de la ligne : le libellé de la ligne est lu.
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                // Décoratif (ADR-030) : coche redondante : l'état sélectionné est porté par selectable (bouton radio).
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Autorisation d'afficher des notifications, demandée quand une cloche est activée ([activations]),
 * jamais au démarrage (ADR-020). Refusée, la cloche garde son réglage et un court message propose
 * d'ouvrir les réglages. C'est la même autorisation que celle des téléchargements (ADR-019).
 */
@Composable
internal fun NotificationPermissionRequest(activations: Flow<Unit>) {
    val context = LocalContext.current
    var showDenied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) showDenied = true
    }
    LaunchedEffect(activations) {
        activations.collect {
            when {
                NotificationManagerCompat.from(context).areNotificationsEnabled() -> Unit
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED ->
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                // Autorisation accordée mais notifications coupées dans les réglages du système.
                else -> showDenied = true
            }
        }
    }
    if (showDenied) {
        AlertDialog(
            onDismissRequest = { showDenied = false },
            title = { Text(stringResource(R.string.notifications_denied_title)) },
            text = { Text(stringResource(R.string.notifications_denied_message)) },
            confirmButton = {
                TextButton(colors = redTextButtonColors(), onClick = {
                    showDenied = false
                    openNotificationSettings(context)
                }) { Text(stringResource(R.string.notifications_open_settings)) }
            },
            dismissButton = {
                TextButton(colors = redTextButtonColors(), onClick = { showDenied = false }) { Text(stringResource(R.string.notifications_later)) }
            },
        )
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
