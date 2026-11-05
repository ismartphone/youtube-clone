package com.youtubeclone.app.presentation.premium

import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stripe.android.PaymentConfiguration
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import com.stripe.android.paymentsheet.rememberPaymentSheet
import com.youtubeclone.app.R
import com.youtubeclone.app.presentation.theme.redTextButtonColors
import com.youtubeclone.app.domain.analytics.PaywallSource
import com.youtubeclone.app.domain.analytics.PaywallVariant
import com.youtubeclone.app.domain.model.PaymentMode
import com.youtubeclone.app.presentation.common.BackTopBar
import com.youtubeclone.app.presentation.common.ErrorToast
import com.youtubeclone.app.presentation.theme.YoutubeCloneTheme

// Écran de l'offre Premium (ADR-007). Le paiement est simulé par notre backend (ADR-012) :
// une feuille « Paiement simulé, aucun débit » remplace la fenêtre d'achat de Google Play.

/**
 * [onPurchased] : achat confirmé par le serveur. [onClose] : « Plus tard » ou la croix.
 * Un visiteur doit se connecter avant d'acheter ([onLoginRequired]).
 */
@Composable
fun PaywallScreen(
    onClose: () -> Unit,
    onLoginRequired: () -> Unit,
    onPurchased: () -> Unit,
    modifier: Modifier = Modifier,
    source: PaywallSource = PaywallSource.App,
    embedded: Boolean = false,
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ErrorToast(viewModel.errors)
    LaunchedEffect(source) { viewModel.onShown(source) }

    LaunchedEffect(state.isPurchased) {
        if (state.isPurchased) {
            Toast.makeText(context, R.string.premium_welcome, Toast.LENGTH_SHORT).show()
            onPurchased()
        }
    }

    PaywallContent(
        state = state,
        onSubscribeClick = { if (state.isLoggedIn) viewModel.startPurchase() else onLoginRequired() },
        onClose = onClose,
        embedded = embedded,
        modifier = modifier,
    )
    // Mode Stripe (ADR-014) : la PaymentSheet du SDK Stripe encaisse avec la clé publiable
    // reçue du serveur ; son issue est renvoyée au ViewModel, qui fait vérifier le paiement.
    val paymentSheet = rememberPaymentSheet { result ->
        viewModel.onPaymentOutcome(
            when (result) {
                is PaymentSheetResult.Completed -> PaymentOutcome.Completed
                is PaymentSheetResult.Canceled -> PaymentOutcome.Canceled
                is PaymentSheetResult.Failed -> PaymentOutcome.Failed
            },
        )
    }
    LaunchedEffect(state.stripePayment) {
        val payment = state.stripePayment ?: return@LaunchedEffect
        PaymentConfiguration.init(context, payment.publishableKey)
        paymentSheet.presentWithPaymentIntent(
            payment.clientSecret,
            PaymentSheet.Configuration(merchantDisplayName = context.getString(R.string.premium_name)),
        )
        viewModel.onStripeSheetShown()
    }

    // Mode simulé (repli quand le serveur n'a pas de clé Stripe) : feuille de paiement simulée.
    if (state.checkoutToken != null) {
        SimulatedPaymentSheet(
            isConfirming = state.isBusy,
            onConfirm = viewModel::confirm,
            onDismiss = viewModel::dismissCheckout,
        )
    }
}

@Composable
private fun PaywallContent(
    state: PaywallUiState,
    onSubscribeClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Dans le parcours d'accueil, la barre de progression remplace la barre de titre (ADR-028).
        if (!embedded) BackTopBar(title = stringResource(R.string.premium_screen_title), onBack = onClose)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_premium),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(32.dp),
                )
            }
            Text(
                text = stringResource(R.string.premium_name),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
            // Test A/B : le titre change selon la variante. La variante prix annonce déjà le prix
            // dans son titre : le sous-titre, qui le répéterait, n'est pas affiché.
            val showPriceSubtitle = state.variant != PaywallVariant.PRICE
            Text(
                text = stringResource(
                    when (state.variant) {
                        PaywallVariant.BENEFITS -> R.string.premium_paywall_title
                        PaywallVariant.PRICE -> R.string.premium_paywall_title_price
                    },
                ),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = if (showPriceSubtitle) 0.dp else 32.dp),
            )
            if (showPriceSubtitle) {
                Text(
                    text = stringResource(R.string.premium_price),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Benefit(R.drawable.ic_no_ads, R.string.premium_benefit_no_ads, R.string.premium_benefit_no_ads_detail)
                Benefit(R.drawable.ic_premium, R.string.premium_benefit_exclusive, R.string.premium_benefit_exclusive_detail)
                Benefit(R.drawable.ic_background_play, R.string.premium_benefit_background, R.string.premium_benefit_background_detail)
                Benefit(R.drawable.ic_download, R.string.premium_benefit_downloads, R.string.premium_benefit_downloads_detail)
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.isAlreadyPremium) {
                Text(
                    text = stringResource(R.string.premium_already),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                PrimaryButton(text = stringResource(R.string.premium_close), isBusy = false, onClick = onClose)
            } else {
                PrimaryButton(
                    text = stringResource(
                        if (state.isLoggedIn) R.string.premium_subscribe else R.string.premium_login_to_subscribe,
                    ),
                    isBusy = state.isBusy,
                    onClick = onSubscribeClick,
                )
                TextButton(colors = redTextButtonColors(), onClick = onClose) {
                    Text(
                        text = stringResource(R.string.premium_later),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = stringResource(
                    if (state.paymentMode == PaymentMode.STRIPE) R.string.premium_stripe_note else R.string.premium_simulated_note,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Benefit(@DrawableRes icon: Int, @StringRes title: Int, @StringRes detail: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(detail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Bouton principal blanc sur fond sombre ; un indicateur remplace le texte pendant l'appel. */
@Composable
private fun PrimaryButton(text: String, isBusy: Boolean, onClick: () -> Unit) {
    val container = MaterialTheme.colorScheme.onBackground
    val content = MaterialTheme.colorScheme.background
    Button(
        onClick = onClick,
        enabled = !isBusy,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container,
            disabledContentColor = content,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        if (isBusy) {
            CircularProgressIndicator(color = content, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Text(text, fontWeight = FontWeight.Bold)
        }
    }
}

// Remplace la fenêtre d'achat de Google Play (ADR-012) : rien n'est débité.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimulatedPaymentSheet(isConfirming: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        PaymentSheetContent(isConfirming, onConfirm, onDismiss)
    }
}

@Composable
private fun PaymentSheetContent(isConfirming: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.premium_sheet_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.premium_sheet_offer),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.premium_price),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = stringResource(R.string.premium_simulated_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        PrimaryButton(text = stringResource(R.string.premium_sheet_confirm), isBusy = isConfirming, onClick = onConfirm)
        TextButton(colors = redTextButtonColors(), onClick = onDismiss, enabled = !isConfirming, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.premium_sheet_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(name = "Paywall, connecté", showBackground = true, backgroundColor = 0xFF0F0F0F, widthDp = 360, heightDp = 760)
@Composable
private fun PaywallContentPreview() {
    YoutubeCloneTheme {
        PaywallContent(state = PaywallUiState(isLoggedIn = true), onSubscribeClick = {}, onClose = {})
    }
}

@Preview(name = "Feuille de paiement simulée", showBackground = true, backgroundColor = 0xFF272727, widthDp = 360)
@Composable
private fun PaymentSheetPreview() {
    YoutubeCloneTheme {
        PaymentSheetContent(isConfirming = false, onConfirm = {}, onDismiss = {})
    }
}
