package com.youtubeclone.app.presentation.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.LiveProduct
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

// Produit épinglé et achat pendant un live (ADR-029, étape 3).

/** Prix lisible (« 49,90 € ») : le serveur envoie des centimes et une devise ISO. */
fun formatPrice(priceCents: Int, currency: String): String {
    val format = NumberFormat.getCurrencyInstance(Locale.FRANCE)
    runCatching { format.currency = Currency.getInstance(currency.uppercase()) }
    return format.format(priceCents / 100.0)
}

/**
 * Carte du produit épinglé : image, nom, prix et, selon le rôle, « Acheter » (spectateur) ou
 * « Retirer » (diffuseur). La carte apparaît et disparaît en direct, avec le diffuseur.
 */
@Composable
fun PinnedProductCard(
    product: LiveProduct?,
    isBroadcaster: Boolean,
    isBuying: Boolean,
    isLoggedIn: Boolean,
    onBuy: () -> Unit,
    onLoginRequired: () -> Unit,
    onUnpin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = product != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        // Garde le dernier produit affiché pendant l'animation de sortie.
        val shown = product ?: return@AnimatedVisibility
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = shown.imageUrl,
                // Décoratif (ADR-030) : le sens est porté par le texte voisin, TalkBack ignore l'image.
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = shown.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatPrice(shown.priceCents, shown.currency),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (shown.description.isNotBlank()) {
                    Text(
                        text = shown.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (isBroadcaster) {
                OutlinedButton(onClick = onUnpin) { Text(stringResource(R.string.live_product_unpin)) }
            } else {
                Button(onClick = if (isLoggedIn) onBuy else onLoginRequired, enabled = !isBuying) {
                    if (isBuying) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Text(stringResource(if (isLoggedIn) R.string.live_product_buy else R.string.live_product_buy_login))
                    }
                }
            }
        }
    }
}

/** Choix du produit à épingler, parmi ceux de la chaîne du diffuseur. */
@Composable
fun ProductPickerDialog(
    products: List<LiveProduct>?,
    onPick: (LiveProduct) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.live_product_picker_title)) },
        text = {
            when {
                products == null -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                products.isEmpty() -> Text(
                    text = stringResource(R.string.live_product_picker_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(products, key = { it.id }) { product ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onPick(product) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AsyncImage(
                                model = product.imageUrl,
                                // Décoratif (ADR-030) : le sens est porté par le texte voisin, TalkBack ignore l'image.
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = product.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = formatPrice(product.priceCents, product.currency),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.live_end_cancel)) } },
    )
}

/** Repli simulé du paiement (serveur sans clé Stripe, ADR-014) : rien n'est débité. */
@Composable
fun SimulatedPurchaseDialog(
    product: LiveProduct?,
    isConfirming: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.live_purchase_sheet_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (product != null) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = formatPrice(product.priceCents, product.currency),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    text = stringResource(R.string.live_purchase_sheet_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !isConfirming) {
                if (isConfirming) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Text(stringResource(R.string.live_purchase_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isConfirming) { Text(stringResource(R.string.live_end_cancel)) }
        },
    )
}
