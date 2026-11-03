package com.youtubeclone.app.presentation.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import com.youtubeclone.app.presentation.auth.LoginScreen
import com.youtubeclone.app.presentation.auth.RegisterScreen
import com.youtubeclone.app.presentation.channel.ChannelScreen
import com.youtubeclone.app.presentation.channel.CreateChannelScreen
import com.youtubeclone.app.presentation.create.CreateSheet
import com.youtubeclone.app.presentation.create.PublishVideoScreen
import com.youtubeclone.app.presentation.create.PublishedBanner
import com.youtubeclone.app.presentation.home.HomeScreen
import com.youtubeclone.app.presentation.onboarding.InterestsScreen
import com.youtubeclone.app.presentation.premium.PaywallScreen
import com.youtubeclone.app.presentation.profile.YouListScreen
import com.youtubeclone.app.presentation.profile.YouScreen
import com.youtubeclone.app.presentation.search.SearchScreen
import com.youtubeclone.app.presentation.settings.SettingsScreen
import com.youtubeclone.app.presentation.shorts.ShortsScreen
import com.youtubeclone.app.presentation.subscriptions.SubscriptionsScreen
import com.youtubeclone.app.presentation.theme.LocalReduceMotion
import com.youtubeclone.app.presentation.theme.Motion
import com.youtubeclone.app.presentation.watch.WatchScreen
import kotlin.reflect.KClass
import kotlinx.serialization.Serializable

// Destinations typées de la navigation.
@Serializable data object HomeRoute
@Serializable data object ShortsRoute
@Serializable data object YouRoute
@Serializable data object LoginRoute
@Serializable data object RegisterRoute
@Serializable data object InterestsRoute
@Serializable data object SubscriptionsRoute
@Serializable data object SettingsRoute
@Serializable data object SearchRoute
@Serializable data class WatchRoute(val videoId: String)
@Serializable data class ChannelRoute(val channelId: String)
// Lecteur Shorts plein écran ouvert sur un Short précis (depuis une page de chaîne).
// Le nom du paramètre est lu par ShortsViewModel.
@Serializable data class ShortsPlayerRoute(val startVideoId: String)
// Offre Premium. [videoId] : vidéo exclusive refusée, rouverte une fois l'achat confirmé.
@Serializable data class PaywallRoute(val videoId: String? = null)
// Création de la chaîne de l'utilisateur connecté.
@Serializable data object CreateChannelRoute
// Publication d'une vidéo choisie dans la galerie. [format] : nom d'une valeur de VideoFormat.
@Serializable data class PublishVideoRoute(val uri: String, val format: String)
// Nom d'une valeur de YouList : historique, vidéos aimées ou vos vidéos.
@Serializable data class YouListRoute(val list: String)

private val FULL_SCREEN_ROUTES = listOf(
    WatchRoute::class,
    ShortsPlayerRoute::class,
    PaywallRoute::class,
    SearchRoute::class,
    LoginRoute::class,
    RegisterRoute::class,
    InterestsRoute::class,
    PublishVideoRoute::class,
    CreateChannelRoute::class,
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
fun AppNavigation(openLoginAtStart: Boolean = false, onLoginOpened: () -> Unit = {}) {
    val navController = rememberNavController()
    TrackScreens(navController)
    LaunchedEffect(openLoginAtStart) {
        if (openLoginAtStart) {
            navController.navigate(LoginRoute)
            onLoginOpened()
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    // L'écran de lecture et les formulaires de compte occupent tout l'écran, sans barre d'onglets.
    val showBottomBar = FULL_SCREEN_ROUTES.none { currentDestination?.hasRoute(it) == true }
    val reduceMotion = LocalReduceMotion.current
    val transitions = remember(reduceMotion) {
        NavTransitions(tabRoutes = Tab.entries.map { it.routeClass }, reduceMotion = reduceMotion)
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
        if (showCreateSheet) {
            CreateSheet(
                onDismiss = { showCreateSheet = false },
                onLoginRequired = { navController.navigate(LoginRoute) },
                onPublishVideo = { uri, format -> navController.navigate(PublishVideoRoute(uri, format.name)) },
                onCreateChannel = { navController.navigate(CreateChannelRoute) },
            )
        }
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(innerPadding),
            enterTransition = transitions.enter,
            exitTransition = transitions.exit,
            popEnterTransition = transitions.popEnter,
            popExitTransition = transitions.popExit,
        ) {
            val openVideo = { videoId: String -> navController.navigate(WatchRoute(videoId)) }
            val openChannel = { channelId: String -> navController.navigate(ChannelRoute(channelId)) }
            val openLogin = { navController.navigate(LoginRoute) }
            val openPaywall = { navController.navigate(PaywallRoute()) }

            composable<HomeRoute> {
                HomeScreen(
                    onVideoClick = openVideo,
                    onChannelClick = openChannel,
                    onAccountClick = { openTab(YouRoute) },
                    onSearchClick = { navController.navigate(SearchRoute) },
                )
            }
            composable<ShortsRoute> {
                ShortsScreen(onChannelClick = openChannel, onLoginRequired = openLogin, onPremiumClick = openPaywall)
            }
            composable<ShortsPlayerRoute> {
                ShortsScreen(onChannelClick = openChannel, onLoginRequired = openLogin, onPremiumClick = openPaywall)
            }
            composable<PaywallRoute> { entry ->
                val videoId = entry.toRoute<PaywallRoute>().videoId
                PaywallScreen(
                    onClose = { navController.popBackStack() },
                    onLoginRequired = openLogin,
                    // Achat ouvert par une vidéo exclusive : elle est rouverte à la place du paywall.
                    onPurchased = {
                        if (videoId != null) {
                            navController.navigate(WatchRoute(videoId)) { popUpTo<PaywallRoute> { inclusive = true } }
                        } else {
                            navController.popBackStack()
                        }
                    },
                )
            }
            composable<SubscriptionsRoute> {
                SubscriptionsScreen(onVideoClick = openVideo, onChannelClick = openChannel, onLoginClick = openLogin)
            }
            composable<WatchRoute> {
                WatchScreen(
                    onVideoClick = openVideo,
                    onChannelClick = openChannel,
                    onLoginRequired = openLogin,
                    // Vidéo exclusive refusée par le serveur : le paywall prend la place de l'écran.
                    onPremiumRequired = { videoId ->
                        navController.navigate(PaywallRoute(videoId)) { popUpTo<WatchRoute> { inclusive = true } }
                    },
                    onRemoveAdsClick = openPaywall,
                )
            }
            composable<ChannelRoute> {
                ChannelScreen(
                    onVideoClick = openVideo,
                    onShortClick = { videoId -> navController.navigate(ShortsPlayerRoute(videoId)) },
                    onLoginRequired = openLogin,
                )
            }
            composable<YouRoute> {
                YouScreen(
                    onLoginClick = { navController.navigate(LoginRoute) },
                    onRegisterClick = { navController.navigate(RegisterRoute) },
                    onEditInterests = { navController.navigate(InterestsRoute) },
                    onChannelClick = openChannel,
                    onListClick = { list -> navController.navigate(YouListRoute(list.name)) },
                    onSubscriptionsClick = { openTab(SubscriptionsRoute) },
                    onPremiumClick = openPaywall,
                    onSettingsClick = { navController.navigate(SettingsRoute) },
                    onCreateChannel = { navController.navigate(CreateChannelRoute) },
                )
            }
            composable<SettingsRoute> { SettingsScreen() }
            composable<SearchRoute> {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onVideoClick = openVideo,
                    onChannelClick = openChannel,
                )
            }
            composable<YouListRoute> { YouListScreen(onVideoClick = openVideo) }
            // Connexion et inscription s'ouvrent par-dessus l'écran qui les demande, et y reviennent une fois
            // la session ouverte. Pour exiger un compte avant une action : navController.navigate(LoginRoute).
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
                        navController.navigate(LoginRoute) { popUpTo<RegisterRoute> { inclusive = true } }
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
            composable<InterestsRoute> {
                InterestsScreen(onSaved = { navController.popBackStack() })
            }
        }
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
