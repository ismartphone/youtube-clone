package com.youtubeclone.app.presentation.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.DEFAULT_AD_CTA
import com.youtubeclone.app.presentation.account.AccountSheet
import com.youtubeclone.app.presentation.account.AccountsEvent
import com.youtubeclone.app.presentation.account.AccountsViewModel
import com.youtubeclone.app.presentation.ads.AdPlayerScreen
import com.youtubeclone.app.presentation.auth.LoginScreen
import com.youtubeclone.app.presentation.auth.RegisterScreen
import com.youtubeclone.app.presentation.channel.ChannelScreen
import com.youtubeclone.app.presentation.channel.CreateChannelScreen
import com.youtubeclone.app.presentation.channel.CustomizeChannelScreen
import com.youtubeclone.app.presentation.create.ComposePostScreen
import com.youtubeclone.app.presentation.create.CreateSheet
import com.youtubeclone.app.presentation.create.PublishVideoScreen
import com.youtubeclone.app.presentation.create.PublishedBanner
import com.youtubeclone.app.presentation.downloads.DownloadsScreen
import com.youtubeclone.app.presentation.downloads.OfflinePlayerScreen
import com.youtubeclone.app.presentation.help.HelpScreen
import com.youtubeclone.app.presentation.home.HomeScreen
import com.youtubeclone.app.presentation.messaging.ConversationScreen
import com.youtubeclone.app.presentation.messaging.ConversationsScreen
import com.youtubeclone.app.presentation.messaging.NewConversationScreen
import com.youtubeclone.app.presentation.onboarding.InterestsScreen
import com.youtubeclone.app.presentation.playlist.PlaylistScreen
import com.youtubeclone.app.presentation.premium.PaywallScreen
import com.youtubeclone.app.presentation.profile.YouListScreen
import com.youtubeclone.app.presentation.profile.YouScreen
import com.youtubeclone.app.presentation.search.SearchScreen
import com.youtubeclone.app.presentation.settings.SettingsScreen
import com.youtubeclone.app.presentation.shorts.ShortsScreen
import com.youtubeclone.app.presentation.subscriptions.SubscriptionsScreen
import com.youtubeclone.app.presentation.theme.LocalReduceMotion
import com.youtubeclone.app.presentation.theme.Motion
import com.youtubeclone.app.presentation.watch.MiniPlayerHeight
import com.youtubeclone.app.presentation.watch.NowPlayingLayer
import com.youtubeclone.app.presentation.watch.WatchViewModel
import kotlin.reflect.KClass
import kotlinx.serialization.Serializable

// Destinations typées de la navigation.
@Serializable data object HomeRoute
@Serializable data object ShortsRoute
@Serializable data object YouRoute
// [email] : connexion préremplie, pour un compte enregistré dont la session a expiré (ADR-022).
@Serializable data class LoginRoute(val email: String? = null)
@Serializable data object RegisterRoute
@Serializable data object InterestsRoute
@Serializable data object SubscriptionsRoute
@Serializable data object SettingsRoute
@Serializable data object SearchRoute
// Publicité du bandeau sponsorisé, lue en plein lecteur (ADR-021).
@Serializable data class AdPlayerRoute(
    val adId: String,
    val title: String,
    val streamUrl: String,
    val isHls: Boolean,
    val durationSeconds: Int,
    val destinationUrl: String? = null,
    val advertiserName: String? = null,
    val ctaLabel: String = DEFAULT_AD_CTA,
)
// [tab] : onglet ouvert d'emblée, par exemple « Posts » depuis un post du fil ; null : Vidéos.
@Serializable data class ChannelRoute(val channelId: String, val tab: String? = null)
// Lecteur Shorts plein écran ouvert sur un Short précis (depuis une page de chaîne).
// Le nom du paramètre est lu par ShortsViewModel.
@Serializable data class ShortsPlayerRoute(val startVideoId: String)
// Offre Premium. [videoId] : vidéo exclusive refusée, rouverte une fois l'achat confirmé.
@Serializable data class PaywallRoute(val videoId: String? = null)
// Création de la chaîne de l'utilisateur connecté.
@Serializable data object CreateChannelRoute
// Une playlist de l'utilisateur (ADR-019). Le nom du paramètre est lu par PlaylistViewModel.
@Serializable data class PlaylistRoute(val playlistId: String)
// Téléchargements et lecture hors ligne (ADR-019). Le nom du paramètre est lu par OfflinePlayerViewModel.
@Serializable data object DownloadsRoute
@Serializable data class OfflinePlayerRoute(val videoId: String)
// Aide : questions fréquentes (ADR-019).
@Serializable data object HelpRoute
// Personnalisation de sa chaîne (ADR-018).
@Serializable data object CustomizeChannelRoute
// Rédaction d'un post sur sa chaîne.
@Serializable data object ComposePostRoute
// Publication d'une vidéo choisie dans la galerie. [format] : nom d'une valeur de VideoFormat.
@Serializable data class PublishVideoRoute(val uri: String, val format: String)
// Nom d'une valeur de YouList : historique, vidéos aimées ou vos vidéos.
@Serializable data class YouListRoute(val list: String)

// Messagerie (ADR-025) : liste des conversations, recherche d'un destinataire, conversation.
@Serializable data object ConversationsRoute
// [shareVideoId] : vidéo à envoyer à la personne choisie, venue de « Envoyer à » (ADR-025, étape 2).
@Serializable data class NewConversationRoute(val shareVideoId: String? = null)
@Serializable data class ConversationRoute(val conversationId: String)

private val FULL_SCREEN_ROUTES = listOf(
    ConversationsRoute::class,
    NewConversationRoute::class,
    ConversationRoute::class,
    AdPlayerRoute::class,
    ShortsPlayerRoute::class,
    PaywallRoute::class,
    SearchRoute::class,
    LoginRoute::class,
    RegisterRoute::class,
    InterestsRoute::class,
    PublishVideoRoute::class,
    CreateChannelRoute::class,
    CustomizeChannelRoute::class,
    ComposePostRoute::class,
)

// Écrans qui prennent la place du mini-lecteur : il s'efface et la lecture se met en pause (ADR-024).
private val PLAYER_HIDDEN_ROUTES = listOf(
    ShortsRoute::class,
    ShortsPlayerRoute::class,
    PublishVideoRoute::class,
    PaywallRoute::class,
    AdPlayerRoute::class,
    OfflinePlayerRoute::class,
)

private enum class Tab(
    val route: Any,
    val routeClass: KClass<*>,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
) {
    Home(HomeRoute, HomeRoute::class, R.string.tab_home, R.drawable.ic_tab_home),
    Shorts(ShortsRoute, ShortsRoute::class, R.string.tab_shorts, R.drawable.ic_tab_shorts),
    Subscriptions(SubscriptionsRoute, SubscriptionsRoute::class, R.string.tab_subscriptions, R.drawable.ic_tab_subscriptions),
    You(YouRoute, YouRoute::class, R.string.tab_you, R.drawable.ic_tab_you),
}

/** [openLoginAtStart] : ouvre la connexion au-dessus du fil d'accueil, puis appelle [onLoginOpened]. */
@Composable
fun AppNavigation(
    openLoginAtStart: Boolean = false,
    onLoginOpened: () -> Unit = {},
    notifiedVideoId: String? = null,
    onNotifiedVideoOpened: () -> Unit = {},
    notifiedConversationId: String? = null,
    onNotifiedConversationOpened: () -> Unit = {},
) {
    val navController = rememberNavController()
    TrackScreens(navController)
    // Lecteur en cours (ADR-024) : lié à l'activité, partagé par l'écran de lecture et le mini-lecteur.
    val watchViewModel: WatchViewModel = hiltViewModel()
    val nowPlayingId by watchViewModel.currentVideoId.collectAsStateWithLifecycle()
    val nowExpanded by watchViewModel.expanded.collectAsStateWithLifecycle()
    val screenTracking: ScreenTrackingViewModel = hiltViewModel()
    LaunchedEffect(nowPlayingId) { if (nowPlayingId != null) screenTracking.onScreen("Watch") }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // Ouvrir une vidéo : l'écran de lecture se déploie par-dessus la navigation, qui reste en place.
    // Le clavier d'une recherche est fermé, sinon il resterait ouvert sous l'écran de lecture.
    val openVideo = { videoId: String ->
        focusManager.clearFocus()
        keyboard?.hide()
        watchViewModel.open(videoId)
    }
    LaunchedEffect(openLoginAtStart) {
        if (openLoginAtStart) {
            navController.navigate(LoginRoute())
            onLoginOpened()
        }
    }
    // Appui sur une notification de publication : la vidéo s'ouvre par-dessus l'écran courant. Depuis
    // un écran plein espace (Shorts, publication, paywall...), on revient d'abord au fil : sinon ses
    // vidéos continueraient de jouer sous l'écran de lecture.
    LaunchedEffect(notifiedVideoId) {
        if (notifiedVideoId != null) {
            val destination = navController.currentDestination
            if (destination != null && PLAYER_HIDDEN_ROUTES.any { destination.hasRoute(it) }) {
                navController.navigate(HomeRoute) { popUpTo(navController.graph.id) { inclusive = true } }
            }
            openVideo(notifiedVideoId)
            onNotifiedVideoOpened()
        }
    }
    // Appui sur une notification de message (ADR-025) : la conversation s'ouvre par-dessus.
    LaunchedEffect(notifiedConversationId) {
        if (notifiedConversationId != null) {
            navController.navigate(ConversationRoute(notifiedConversationId))
            onNotifiedConversationOpened()
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    // L'écran de lecture déployé et les formulaires de compte occupent tout l'écran, sans barre d'onglets.
    val showBottomBar = FULL_SCREEN_ROUTES.none { currentDestination?.hasRoute(it) == true } &&
        !(nowPlayingId != null && nowExpanded)
    // Écran plein espace (Shorts, publication, paywall, annonce du bandeau, hors ligne) : le
    // mini-lecteur s'efface, et la lecture se met en pause. Le lecteur hors ligne le ferme.
    val onHiddenRoute = PLAYER_HIDDEN_ROUTES.any { currentDestination?.hasRoute(it) == true }
    val onOfflineRoute = currentDestination?.hasRoute(OfflinePlayerRoute::class) == true
    LaunchedEffect(onHiddenRoute, nowExpanded) { watchViewModel.setSuppressed(onHiddenRoute && !nowExpanded) }
    LaunchedEffect(onOfflineRoute) { if (onOfflineRoute) watchViewModel.close() }
    // Le clavier ouvert (recherche, conversation) masque aussi le mini-lecteur : il couvrirait le champ.
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val miniVisible = !onHiddenRoute && !imeOpen
    val miniInset = if (nowPlayingId != null && !nowExpanded && miniVisible) MiniPlayerHeight else 0.dp
    // Actions de l'écran de lecture et des écrans, partagées hors du graphe de navigation.
    val openChannel = { channelId: String -> navController.navigate(ChannelRoute(channelId)) }
    val openLogin = { navController.navigate(LoginRoute()) }
    val openPaywall = { navController.navigate(PaywallRoute()) }
    // « Envoyer à », puis « Rechercher quelqu'un d'autre » (ADR-025, étape 2).
    val shareToSomeone = { videoId: String -> navController.navigate(NewConversationRoute(shareVideoId = videoId)) }
    val reduceMotion = LocalReduceMotion.current
    val transitions = remember(reduceMotion) {
        NavTransitions(tabRoutes = Tab.entries.map { it.routeClass }, reduceMotion = reduceMotion)
    }
    // Comptes enregistrés sur l'appareil (ADR-022) : une feuille partagée par le fil et Vous.
    val accountsViewModel: AccountsViewModel = hiltViewModel()
    val accounts by accountsViewModel.accounts.collectAsStateWithLifecycle()
    var showAccountSheet by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(accountsViewModel) {
        accountsViewModel.events.collect { event ->
            when (event) {
                is AccountsEvent.OpenLogin -> navController.navigate(LoginRoute(event.email))
            }
        }
    }
    // Changement de compte : la lecture s'arrête et l'application revient au fil, pile vidée,
    // onglets compris (leurs écrans gardés montraient les données de l'ancien compte).
    LaunchedEffect(accountsViewModel) {
        accountsViewModel.accountChanged.collect {
            showAccountSheet = false
            watchViewModel.close()
            navController.navigate(HomeRoute) { popUpTo(navController.graph.id) { inclusive = true } }
            listOf(ShortsRoute, SubscriptionsRoute, YouRoute).forEach { navController.clearBackStack(it) }
        }
    }
    // Feuille de création du bouton + (ADR-015), ouverte par-dessus l'onglet courant.
    var showCreateSheet by rememberSaveable { mutableStateOf(false) }
    // Un seul exemplaire de chaque onglet, état conservé entre les onglets (défilement du fil...).
    // L'onglet s'ouvre toujours sur son écran principal : les pages empilées par-dessus (chaîne,
    // Short, liste...) sont retirées, sinon « Vous » rouvrirait la dernière chaîne visitée.
    val openTab = { route: Any ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        navController.popBackStack(route, inclusive = false)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                Tab.entries.forEach { tab ->
                    // Le + s'insère au centre, entre Shorts et Abonnements.
                    if (tab == Tab.Subscriptions) CreateItem(onClick = { showCreateSheet = true })
                    val selected = currentDestination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = { openTab(tab.route) },
                        icon = { TabIcon(tab, selected) },
                        label = { TabLabel(tab, selected) },
                        // Couleurs gérées par TabIcon et TabLabel, pour animer le passage du gris au blanc.
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        if (showAccountSheet) {
            AccountSheet(
                accounts = accounts,
                onSelect = accountsViewModel::select,
                onAddAccount = { navController.navigate(LoginRoute()) },
                onRemove = accountsViewModel::remove,
                onDismiss = { showAccountSheet = false },
            )
        }
        // Avatar du fil ou de Vous : la feuille des comptes, s'il y en a d'enregistrés sur l'appareil.
        val openAccounts: () -> Unit = { if (accounts.isEmpty()) openTab(YouRoute) else showAccountSheet = true }
        if (showCreateSheet) {
            CreateSheet(
                onDismiss = { showCreateSheet = false },
                onLoginRequired = { navController.navigate(LoginRoute()) },
                onPublishVideo = { uri, format -> navController.navigate(PublishVideoRoute(uri, format.name)) },
                onCreateChannel = { navController.navigate(CreateChannelRoute) },
                onWritePost = { navController.navigate(ComposePostRoute) },
            )
        }
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(innerPadding).padding(bottom = miniInset),
            enterTransition = transitions.enter,
            exitTransition = transitions.exit,
            popEnterTransition = transitions.popEnter,
            popExitTransition = transitions.popExit,
        ) {
            val openChannelPosts = { channelId: String -> navController.navigate(ChannelRoute(channelId, tab = "Posts")) }

            composable<HomeRoute> {
                HomeScreen(
                    onVideoClick = openVideo,
                    onChannelClick = openChannel,
                    onAccountClick = openAccounts,
                    onSearchClick = { navController.navigate(SearchRoute) },
                    onSponsoredAdPlay = { ad ->
                        navController.navigate(
                            AdPlayerRoute(
                                ad.id,
                                ad.title,
                                ad.streamUrl,
                                ad.isHls,
                                ad.durationSeconds,
                                ad.destinationUrl,
                                ad.advertiserName,
                                ad.ctaLabel,
                            ),
                        )
                    },
                    onRemoveAdsClick = openPaywall,
                    onPostClick = openChannelPosts,
                    onMessagesClick = { navController.navigate(ConversationsRoute) },
                    onLoginRequired = openLogin,
                )
            }
            composable<ConversationsRoute> {
                ConversationsScreen(
                    onBack = { navController.popBackStack() },
                    onConversationClick = { id -> navController.navigate(ConversationRoute(id)) },
                    onNewConversation = { navController.navigate(NewConversationRoute()) },
                )
            }
            composable<NewConversationRoute> {
                NewConversationScreen(
                    onBack = { navController.popBackStack() },
                    // La recherche cède la place à la conversation : Retour ramène à la liste.
                    onConversationOpened = { id ->
                        navController.navigate(ConversationRoute(id)) { popUpTo<NewConversationRoute> { inclusive = true } }
                    },
                )
            }
            composable<ConversationRoute> {
                ConversationScreen(
                    onBack = { navController.popBackStack() },
                    // Un Short partagé s'ouvre dans le lecteur des Shorts.
                    onOpenVideo = { video ->
                        if (video.isShort) navController.navigate(ShortsPlayerRoute(video.id)) else openVideo(video.id)
                    },
                )
            }
            composable<AdPlayerRoute> {
                AdPlayerScreen(onClose = { navController.popBackStack() })
            }
            composable<ShortsRoute> {
                ShortsScreen(
                    onChannelClick = openChannel,
                    onLoginRequired = openLogin,
                    onPremiumClick = openPaywall,
                    onShareToSomeone = shareToSomeone,
                )
            }
            composable<ShortsPlayerRoute> {
                ShortsScreen(
                    onChannelClick = openChannel,
                    onLoginRequired = openLogin,
                    onPremiumClick = openPaywall,
                    onShareToSomeone = shareToSomeone,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<PaywallRoute> { entry ->
                val videoId = entry.toRoute<PaywallRoute>().videoId
                PaywallScreen(
                    onClose = { navController.popBackStack() },
                    onLoginRequired = openLogin,
                    // Achat ouvert par une vidéo exclusive : elle est rouverte à la place du paywall.
                    onPurchased = {
                        navController.popBackStack()
                        if (videoId != null) openVideo(videoId)
                    },
                )
            }
            composable<SubscriptionsRoute> {
                SubscriptionsScreen(
                    onVideoClick = openVideo,
                    onChannelClick = openChannel,
                    onPostClick = openChannelPosts,
                    onLoginClick = openLogin,
                )
            }
            composable<ChannelRoute> {
                ChannelScreen(
                    onBack = { navController.popBackStack() },
                    onVideoClick = openVideo,
                    onShortClick = { videoId -> navController.navigate(ShortsPlayerRoute(videoId)) },
                    onLoginRequired = openLogin,
                    onCustomizeClick = { navController.navigate(CustomizeChannelRoute) },
                )
            }
            composable<YouRoute> {
                YouScreen(
                    onLoginClick = { navController.navigate(LoginRoute()) },
                    onRegisterClick = { navController.navigate(RegisterRoute) },
                    onEditInterests = { navController.navigate(InterestsRoute) },
                    onChannelClick = openChannel,
                    onCustomizeChannel = { navController.navigate(CustomizeChannelRoute) },
                    onHelpClick = { navController.navigate(HelpRoute) },
                    onPlaylistClick = { playlistId -> navController.navigate(PlaylistRoute(playlistId)) },
                    onDownloadsClick = { navController.navigate(DownloadsRoute) },
                    onVideoClick = openVideo,
                    onListClick = { list -> navController.navigate(YouListRoute(list.name)) },
                    onSubscriptionsClick = { openTab(SubscriptionsRoute) },
                    onPremiumClick = openPaywall,
                    onSettingsClick = { navController.navigate(SettingsRoute) },
                    onCreateChannel = { navController.navigate(CreateChannelRoute) },
                    onAccountClick = openAccounts,
                )
            }
            composable<SettingsRoute> { SettingsScreen(onBack = { navController.popBackStack() }) }
            composable<SearchRoute> {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onVideoClick = openVideo,
                    onChannelClick = openChannel,
                )
            }
            composable<YouListRoute> {
                YouListScreen(onBack = { navController.popBackStack() }, onVideoClick = openVideo)
            }
            // Connexion et inscription s'ouvrent par-dessus l'écran qui les demande, et y reviennent une fois
            // la session ouverte. Pour exiger un compte avant une action : navController.navigate(LoginRoute()).
            composable<LoginRoute> {
                LoginScreen(
                    onLoggedIn = { navController.popBackStack() },
                    onRegisterClick = {
                        navController.navigate(RegisterRoute) { popUpTo<LoginRoute> { inclusive = true } }
                    },
                    // Visiteur : retour à l'écran d'où venait la demande, ou au fil d'accueil.
                    onContinueAsVisitor = { navController.popBackStack() },
                )
            }
            composable<RegisterRoute> {
                RegisterScreen(
                    onRegistered = { navController.popBackStack() },
                    onLoginClick = {
                        navController.navigate(LoginRoute()) { popUpTo<RegisterRoute> { inclusive = true } }
                    },
                    onContinueAsVisitor = { navController.popBackStack() },
                )
            }
            composable<PublishVideoRoute> {
                PublishVideoScreen(
                    onClose = { navController.popBackStack() },
                    // Retour à l'écran d'où le créateur était parti, où s'affiche la confirmation.
                    onPublished = { navController.popBackStack() },
                )
            }
            composable<ComposePostRoute> {
                ComposePostScreen(
                    onClose = { navController.popBackStack() },
                    // Retour à l'écran d'où le créateur était parti, où s'affiche la confirmation.
                    onPublished = { navController.popBackStack() },
                )
            }
            composable<CreateChannelRoute> {
                CreateChannelScreen(
                    onClose = { navController.popBackStack() },
                    // La page de la nouvelle chaîne remplace le formulaire : Retour ramène d'où l'on venait.
                    onCreated = { channelId ->
                        navController.navigate(ChannelRoute(channelId)) {
                            popUpTo<CreateChannelRoute> { inclusive = true }
                        }
                    },
                )
            }
            composable<PlaylistRoute> {
                PlaylistScreen(onBack = { navController.popBackStack() }, onVideoClick = openVideo)
            }
            composable<DownloadsRoute> {
                DownloadsScreen(
                    onBack = { navController.popBackStack() },
                    onPlay = { videoId -> navController.navigate(OfflinePlayerRoute(videoId)) },
                )
            }
            composable<OfflinePlayerRoute> { OfflinePlayerScreen(onBack = { navController.popBackStack() }) }
            composable<HelpRoute> { HelpScreen(onBack = { navController.popBackStack() }) }
            composable<CustomizeChannelRoute> {
                CustomizeChannelScreen(onClose = { navController.popBackStack() })
            }
            composable<InterestsRoute> {
                InterestsScreen(
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }
        }
        // Écran de lecture et mini-lecteur (ADR-024) : une couche au-dessus de la navigation.
        NowPlayingLayer(
            miniVisible = miniVisible,
            onChannelClick = openChannel,
            onLoginRequired = openLogin,
            // Vidéo exclusive refusée par le serveur : la vidéo est fermée, le paywall la remplace.
            onPremiumRequired = { videoId -> navController.navigate(PaywallRoute(videoId)) },
            onRemoveAdsClick = openPaywall,
            onOpenDownloads = { navController.navigate(DownloadsRoute) },
            onShareToSomeone = shareToSomeone,
            modifier = Modifier.padding(innerPadding),
            viewModel = watchViewModel,
        )
        // Confirmation d'une publication, par-dessus l'écran d'où le créateur était parti.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.BottomCenter,
        ) {
            PublishedBanner()
        }
    }
}

// Bouton + de la barre d'onglets : il n'ouvre pas d'onglet mais la feuille de création.
// Mis en valeur par un disque clair, sans libellé visible.
@Composable
private fun RowScope.CreateItem(onClick: () -> Unit) {
    val label = stringResource(R.string.tab_create)
    NavigationBarItem(
        selected = false,
        onClick = onClick,
        icon = {
            Box(
                modifier = Modifier
                    .size(CREATE_BUTTON_SIZE)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.background,
                )
            }
        },
        colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent),
    )
}

private val CREATE_BUTTON_SIZE = 40.dp

// Onglet sélectionné : l'icône grossit légèrement (sauf animations réduites), et la couleur
// passe du gris au blanc en fondu.
private const val SELECTED_ICON_SCALE = 1.12f

@Composable
private fun tabColor(selected: Boolean) = animateColorAsState(
    targetValue = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
    animationSpec = tween(Motion.FADE_MS, easing = Motion.Easing),
    label = "tabColor",
)

@Composable
private fun TabIcon(tab: Tab, selected: Boolean) {
    val color by tabColor(selected)
    val growWhenSelected = !LocalReduceMotion.current
    val scale by animateFloatAsState(
        targetValue = if (selected && growWhenSelected) SELECTED_ICON_SCALE else 1f,
        animationSpec = tween(Motion.FADE_MS, easing = Motion.Easing),
        label = "tabIconScale",
    )
    Icon(
        painter = painterResource(tab.icon),
        contentDescription = null,
        tint = color,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    )
}

@Composable
private fun TabLabel(tab: Tab, selected: Boolean) {
    val color by tabColor(selected)
    // 10sp : « Abonnements » tient entier. Une seule ligne, tronquée en dernier recours
    // (police agrandie dans les réglages d'accessibilité).
    Text(
        text = stringResource(tab.label),
        color = color,
        fontSize = 10.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
