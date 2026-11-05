package com.youtubeclone.app.presentation.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.youtubeclone.app.R
import com.youtubeclone.app.presentation.theme.YoutubeRedFill
import com.youtubeclone.app.presentation.theme.redTextButtonColors
import com.youtubeclone.app.domain.model.Account
import com.youtubeclone.app.domain.model.MAX_ACCOUNTS

/**
 * Feuille des comptes enregistrés sur l'appareil (ADR-022), ouverte par l'avatar du fil ou de
 * Vous : avatar, nom, @handle (ou email sans chaîne), coche sur le compte actif. Un compte dont la
 * session a expiré est grisé, « Reconnexion requise ». Dessous, « Ajouter un compte » et « Gérer
 * les comptes », qui passe la liste en mode retrait.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(
    accounts: List<Account>,
    onSelect: (Account) -> Unit,
    onAddAccount: () -> Unit,
    onRemove: (Account) -> Unit,
    onDismiss: () -> Unit,
) {
    var managing by rememberSaveable { mutableStateOf(false) }
    var toRemove by remember { mutableStateOf<Account?>(null) }

    toRemove?.let { account ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text(stringResource(R.string.accounts_remove_title)) },
            text = { Text(stringResource(R.string.accounts_remove_text, account.displayName.ifBlank { account.email })) },
            confirmButton = {
                TextButton(colors = redTextButtonColors(), onClick = {
                    onRemove(account)
                    toRemove = null
                    // Le compte actif retiré, c'est une déconnexion : la feuille se ferme.
                    if (account.isActive || accounts.size <= 1) onDismiss()
                }) { Text(stringResource(R.string.accounts_remove)) }
            },
            dismissButton = { TextButton(colors = redTextButtonColors(), onClick = { toRemove = null }) { Text(stringResource(R.string.accounts_cancel)) } },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(if (managing) R.string.accounts_manage else R.string.accounts_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                )
                if (managing) {
                    TextButton(colors = redTextButtonColors(), onClick = { managing = false }) { Text(stringResource(R.string.accounts_manage_done)) }
                }
            }
            accounts.forEach { account ->
                AccountRow(
                    account = account,
                    managing = managing,
                    onClick = {
                        if (!managing) {
                            onSelect(account)
                            onDismiss()
                        }
                    },
                    onRemove = { toRemove = account },
                )
            }
            if (!managing) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                if (accounts.size < MAX_ACCOUNTS) {
                    SheetAction(R.drawable.ic_add, R.string.accounts_add) {
                        onDismiss()
                        onAddAccount()
                    }
                } else {
                    Text(
                        text = stringResource(R.string.accounts_limit, MAX_ACCOUNTS),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
                SheetAction(R.drawable.ic_person_remove, R.string.accounts_manage) { managing = true }
            }
        }
    }
}

@Composable
private fun AccountRow(account: Account, managing: Boolean, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !managing, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Compte à reconnecter : grisé, mais toujours utilisable (appui = connexion préremplie).
        Row(
            modifier = Modifier.weight(1f).alpha(if (account.needsReconnection) 0.5f else 1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AccountAvatar(account)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.displayName.ifBlank { account.email },
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when {
                        account.needsReconnection -> stringResource(R.string.accounts_reconnect)
                        account.handle != null -> "@${account.handle}"
                        else -> account.email
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when {
            managing -> TextButton(colors = redTextButtonColors(), onClick = onRemove) { Text(stringResource(R.string.accounts_remove)) }
            account.isActive -> Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = stringResource(R.string.accounts_active),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

// Photo du compte, initiale à défaut.
@Composable
private fun AccountAvatar(account: Account) {
    val modifier = Modifier.size(40.dp).clip(CircleShape)
    if (account.avatarUrl != null) {
        AsyncImage(
            model = account.avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.background(MaterialTheme.colorScheme.surface),
        )
    } else {
        Box(modifier = modifier.background(YoutubeRedFill), contentAlignment = Alignment.Center) {
            Text(
                text = account.displayName.ifBlank { account.email }.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun SheetAction(icon: Int, label: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.padding(start = 8.dp).size(24.dp))
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
    }
}
