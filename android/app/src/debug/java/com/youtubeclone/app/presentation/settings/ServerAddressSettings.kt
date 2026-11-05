package com.youtubeclone.app.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.youtubeclone.app.R
import com.youtubeclone.app.data.server.ServerAddress
import com.youtubeclone.app.presentation.common.UiError
import kotlinx.serialization.Serializable

// Version debug (ADR-027) des points d'entrée du réglage de l'adresse du serveur.
// La version release, vide, se trouve dans src/release.

@Serializable
data object ServerAddressRoute

private val LocalOpenServerAddress = staticCompositionLocalOf<(() -> Unit)?> { null }

/** Ligne « Adresse du serveur » de Vous → Paramètres, avec l'adresse utilisée en détail. */
@Composable
fun ServerAddressSettingsRow(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = stringResource(R.string.server_address_title),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = ServerAddress.baseUrl,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

fun NavGraphBuilder.serverAddressDestination(navController: NavController) {
    composable<ServerAddressRoute> { ServerAddressScreen(onBack = { navController.popBackStack() }) }
}

fun NavController.openServerAddress() {
    navigate(ServerAddressRoute)
}

/** Sous le message d'erreur réseau : lien vers le réglage. Rien pour les autres erreurs. */
@Composable
fun ServerAddressLink(error: UiError) {
    val open = LocalOpenServerAddress.current ?: return
    if (error.messageRes != R.string.error_network) return
    TextButton(onClick = open) { Text(stringResource(R.string.server_address_link)) }
}

@Composable
fun ProvideServerAddressLink(onOpen: () -> Unit, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalOpenServerAddress provides onOpen, content = content)
}
