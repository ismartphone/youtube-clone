package com.youtubeclone.app.presentation.live

import android.view.View
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import android.widget.Toast
import coil3.compose.AsyncImage
import com.stripe.android.PaymentConfiguration
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import com.stripe.android.paymentsheet.rememberPaymentSheet
import com.youtubeclone.app.R
import com.youtubeclone.app.presentation.theme.YoutubeRedText
import com.youtubeclone.app.presentation.theme.YoutubeRedFill
import com.youtubeclone.app.presentation.theme.redTextButtonColors
import com.youtubeclone.app.presentation.theme.redButtonColors
import com.youtubeclone.app.domain.model.LiveAuthor
import com.youtubeclone.app.domain.model.LiveChatMessage
import com.youtubeclone.app.presentation.common.ChannelAvatar
import com.youtubeclone.app.presentation.common.ErrorState
import com.youtubeclone.app.presentation.common.ErrorToast
import com.youtubeclone.app.presentation.common.LoadingState

/**
 * Écran d'un live (ADR-029) : la vidéo en haut, avec « EN DIRECT » et les spectateurs, puis le
 * titre et la chaîne, et en dessous le chat. Un visiteur lit sans pouvoir écrire ; le diffuseur
 * peut terminer le live.
 */
@Composable
fun LiveScreen(
    onBack: () -> Unit,
    onLoginRequired: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LiveViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val purchase by viewModel.purchase.collectAsStateWithLifecycle()
    val ownProducts by viewModel.ownProducts.collectAsStateWithLifecycle()
    ErrorToast(viewModel.errors)
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.notices.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    // Mode Stripe (ADR-014) : la PaymentSheet encaisse avec la clé publiable reçue du serveur ; son
    // issue est renvoyée au ViewModel, qui fait vérifier le paiement avant d'enregistrer l'achat.
    val paymentSheet = rememberPaymentSheet { result ->
        viewModel.onStripeOutcome(
            completed = result is PaymentSheetResult.Completed,
            canceled = result is PaymentSheetResult.Canceled,
        )
    }
    val channelName = (state as? LiveUiState.Ready)?.live?.channel?.name.orEmpty()
    LaunchedEffect(purchase.stripePayment) {
        val payment = purchase.stripePayment ?: return@LaunchedEffect
        PaymentConfiguration.init(context, payment.publishableKey)
        paymentSheet.presentWithPaymentIntent(
            payment.clientSecret,
            PaymentSheet.Configuration(merchantDisplayName = channelName),
        )
        viewModel.onStripeSheetShown()
    }
    if (purchase.simulatedToken != null) {
        SimulatedPurchaseDialog(
            product = (state as? LiveUiState.Ready)?.pinnedProduct,
            isConfirming = purchase.isBusy,
            onConfirm = viewModel::confirmSimulated,
            onDismiss = viewModel::dismissSimulated,
        )
    }
    var pickingProduct by rememberSaveable { mutableStateOf(false) }
    if (pickingProduct) {
        ProductPickerDialog(
            products = ownProducts,
            onPick = {
                pickingProduct = false
                viewModel.pin(it)
            },
            onDismiss = { pickingProduct = false },
        )
    }
    // Salle ouverte seulement écran visible : le compteur de spectateurs est celui des gens qui regardent.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.collectLiveUpdates() }
    }
    LifecycleStartEffect(viewModel) {
        viewModel.onShown(true)
        onStopOrDispose { viewModel.onShown(false) }
    }
    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text(stringResource(R.string.live_end_title)) },
            text = { Text(stringResource(R.string.live_end_message)) },
            confirmButton = {
                TextButton(colors = redTextButtonColors(), onClick = {
                    confirmEnd = false
                    viewModel.endLive()
                }) { Text(stringResource(R.string.live_end_confirm)) }
            },
            dismissButton = { TextButton(colors = redTextButtonColors(), onClick = { confirmEnd = false }) { Text(stringResource(R.string.live_end_cancel)) } },
        )
    }

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        when (val current = state) {
            LiveUiState.Loading -> {
                Row(Modifier.fillMaxWidth().padding(4.dp)) { BackButton(onBack) }
                LoadingState(Modifier.weight(1f).fillMaxWidth())
            }
            is LiveUiState.Error -> {
                Row(Modifier.fillMaxWidth().padding(4.dp)) { BackButton(onBack) }
                ErrorState(current.error, viewModel::load, Modifier.weight(1f).fillMaxWidth())
            }
            is LiveUiState.Ready -> {
                LiveVideo(current, viewModel, onBack)
                LiveInfo(current, onEnd = { confirmEnd = true })
                // Produit épinglé : visible chez tous, en direct. Le diffuseur peut épingler ou retirer.
                PinnedProductCard(
                    product = current.pinnedProduct.takeIf { !current.isEnded },
                    isBroadcaster = current.isBroadcaster,
                    isBuying = purchase.isBusy,
                    isLoggedIn = current.myId != null,
                    onBuy = viewModel::startPurchase,
                    onLoginRequired = onLoginRequired,
                    onUnpin = viewModel::unpin,
                )
                if (current.isBroadcaster && !current.isEnded && current.pinnedProduct == null) {
                    OutlinedButton(
                        onClick = {
                            viewModel.loadOwnProducts()
                            pickingProduct = true
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    ) { Text(stringResource(R.string.live_product_pin)) }
                }
                // Les émojis des réactions remontent par-dessus le chat, sans le masquer ni capter les appuis.
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Chat(current, Modifier.fillMaxSize())
                    if (!current.isEnded) {
                        FloatingReactions(viewModel.reactions, Modifier.matchParentSize())
                    }
                }
                if (current.isEnded) {
                    Text(
                        text = stringResource(R.string.live_chat_closed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                } else if (current.myId == null) {
                    // Visiteur : il lit le chat, la connexion est demandée au moment d'écrire.
                    OutlinedButton(
                        onClick = onLoginRequired,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    ) { Text(stringResource(R.string.live_chat_login)) }
                } else {
                    ReactionBar(onReact = viewModel::react)
                    ChatInput(draft, isSending, viewModel::onDraftChange, viewModel::send)
                }
            }
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit, tint: Color = MaterialTheme.colorScheme.onBackground) {
    IconButton(onClick = onBack) {
        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back), tint = tint)
    }
}

// Vidéo 16/9 en haut : flèche de retour, « EN DIRECT » et spectateurs en surcouche. Live terminé : un voile.
@OptIn(UnstableApi::class)
@Composable
private fun LiveVideo(state: LiveUiState.Ready, viewModel: LiveViewModel, onBack: () -> Unit) {
    // Pour TalkBack (ADR-030, D7) : la zone vidéo s'annonce comme un direct, avec sa chaîne et son titre,
    // et non comme un lecteur manipulable (aucun contrôle de lecture n'existe).
    val streamDescription = stringResource(R.string.a11y_live_stream, state.live.title, state.live.channel.name)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black)
            .then(if (state.isEnded) Modifier else Modifier.semantics { contentDescription = streamDescription; role = Role.Image }),
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = viewModel.player
                    // Pas de contrôles : un direct ne se met pas en pause et n'a pas de barre de progression.
                    useController = false
                    keepScreenOn = true
                }
            },
            onRelease = { view -> view.player = null },
            modifier = Modifier.matchParentSize(),
        )
        if (state.isEnded) {
            Box(
                modifier = Modifier.matchParentSize().background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = state.live.stream?.thumbnailUrl,
                    // Décoratif (ADR-030) : le sens est porté par le texte voisin, TalkBack ignore l'image.
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.25f,
                    modifier = Modifier.matchParentSize(),
                )
                Text(
                    text = stringResource(R.string.live_ended),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopStart).padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onBack, tint = Color.White)
            if (!state.isEnded) {
                LiveBadge()
                Box(Modifier.weight(1f))
                ViewerCountPill(state.viewerCount)
            }
        }
    }
}

@Composable
private fun LiveInfo(state: LiveUiState.Ready, onEnd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ChannelAvatar(channel = state.live.channel, modifier = Modifier.size(36.dp).clip(CircleShape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = state.live.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = state.live.channel.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (state.isBroadcaster && !state.isEnded) {
            Button(onClick = onEnd, colors = redButtonColors()) { Text(stringResource(R.string.live_end_button)) }
        }
    }
}

@Composable
private fun Chat(state: LiveUiState.Ready, modifier: Modifier = Modifier) {
    if (state.messages.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.live_chat_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val listState = rememberLazyListState()
    // Un nouveau message ramène en bas, si l'on y était à peu près.
    LaunchedEffect(state.messages.firstOrNull()?.id) {
        if (listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0)
    }
    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        // Calé en bas, comme un chat : peu de messages restent près du champ de saisie.
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
    ) {
        items(state.messages, key = { it.id }) { message -> ChatLine(message) }
    }
}

@Composable
private fun ChatLine(message: LiveChatMessage) {
    if (message.isPurchase) {
        // Annonce écrite par le serveur : jamais le montant ni le moyen de paiement.
        Text(
            text = "\uD83D\uDED2  " + message.content,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = YoutubeRedText,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .padding(horizontal = 10.dp, vertical = 8.dp)
                // L'emoji du panier est décoratif : TalkBack lit seulement l'annonce.
                .semantics { contentDescription = message.content },
        )
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        ChatAvatar(message.author)
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    append(message.author.displayName)
                }
                append("  ")
                append(message.content)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            // Lu « auteur : message » (le texte affiché sépare les deux par des espaces).
            modifier = Modifier.semantics { contentDescription = message.author.displayName + " : " + message.content },
        )
    }
}

@Composable
private fun ChatAvatar(author: LiveAuthor) {
    val modifier = Modifier.size(28.dp).clip(CircleShape)
    if (author.avatarUrl != null) {
        // Décoratif (ADR-030) : le sens est porté par le texte voisin, TalkBack ignore l'image.
        AsyncImage(author.avatarUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        // L'initiale est décorative : le nom de l'auteur est dans le texte du message.
        Box(modifier.background(YoutubeRedFill).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
            Text(
                text = author.displayName.take(1).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun ChatInput(draft: String, isSending: Boolean, onChange: (String) -> Unit, onSend: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onChange,
            placeholder = { Text(stringResource(R.string.live_chat_placeholder)) },
            maxLines = 3,
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSend, enabled = draft.isNotBlank() && !isSending) {
            Icon(painterResource(R.drawable.ic_send), contentDescription = stringResource(R.string.messages_send))
        }
    }
}
