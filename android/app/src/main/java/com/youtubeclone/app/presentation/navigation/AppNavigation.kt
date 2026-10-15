package com.youtubeclone.app.presentation.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.youtubeclone.app.R
import com.youtubeclone.app.presentation.auth.LoginScreen
import com.youtubeclone.app.presentation.auth.RegisterScreen
import com.youtubeclone.app.presentation.channel.ChannelScreen
import com.youtubeclone.app.presentation.home.HomeScreen
import com.youtubeclone.app.presentation.onboarding.InterestsScreen
import com.youtubeclone.app.presentation.library.LibraryScreen
import com.youtubeclone.app.presentation.profile.YouScreen
import com.youtubeclone.app.presentation.shorts.ShortsScreen
import com.youtubeclone.app.presentation.subscriptions.SubscriptionsScreen
import com.youtubeclone.app.presentation.watch.WatchScreen
import kotlin.reflect.KClass
import kotlinx.serialization.Serializable

// Destinations typées de la navigation.
@Serializable data object HomeRoute
@Serializable data object ShortsRoute
@Serializable data object LibraryRoute
@Serializable data object YouRoute
@Serializable data object LoginRoute
@Serializable data object RegisterRoute
@Serializable data object InterestsRoute
@Serializable data object SubscriptionsRoute
@Serializable data class WatchRoute(val videoId: String)
@Serializable data class ChannelRoute(val channelId: String)

private val FULL_SCREEN_ROUTES = listOf(
    WatchRoute::class,
    LoginRoute::class,
    RegisterRoute::class,
    InterestsRoute::class,
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
    Library(LibraryRoute, LibraryRoute::class, R.string.tab_library, R.drawable.ic_tab_library),
    You(YouRoute, YouRoute::class, R.string.tab_you, R.drawable.ic_tab_you),
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    // L'écran de lecture et les formulaires de compte occupent tout l'écran, sans barre d'onglets.
    val showBottomBar = FULL_SCREEN_ROUTES.none { currentDestination?.hasRoute(it) == true }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                Tab.entries.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                // Un seul exemplaire de chaque onglet, état conservé entre les onglets.
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onBackground,
                            selectedTextColor = MaterialTheme.colorScheme.onBackground,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            val openVideo = { videoId: String -> navController.navigate(WatchRoute(videoId)) }
            val openChannel = { channelId: String -> navController.navigate(ChannelRoute(channelId)) }
            val openLogin = { navController.navigate(LoginRoute) }

            composable<HomeRoute> {
                HomeScreen(onVideoClick = openVideo, onChannelClick = openChannel)
            }
            composable<ShortsRoute> { ShortsScreen() }
            composable<SubscriptionsRoute> {
                SubscriptionsScreen(onVideoClick = openVideo, onChannelClick = openChannel, onLoginClick = openLogin)
            }
            composable<LibraryRoute> { LibraryScreen() }
            composable<WatchRoute> {
                WatchScreen(onChannelClick = openChannel, onLoginRequired = openLogin)
            }
            composable<ChannelRoute> {
                ChannelScreen(onVideoClick = openVideo, onLoginRequired = openLogin)
            }
            composable<YouRoute> {
                YouScreen(
                    onLoginClick = { navController.navigate(LoginRoute) },
                    onRegisterClick = { navController.navigate(RegisterRoute) },
                    onEditInterests = { navController.navigate(InterestsRoute) },
                )
            }
            // Connexion et inscription s'ouvrent par-dessus l'écran qui les demande, et y reviennent une fois
            // la session ouverte. Pour exiger un compte avant une action : navController.navigate(LoginRoute).
            composable<LoginRoute> {
                LoginScreen(
                    onLoggedIn = { navController.popBackStack() },
                    onRegisterClick = {
                        navController.navigate(RegisterRoute) { popUpTo<LoginRoute> { inclusive = true } }
                    },
                )
            }
            composable<RegisterRoute> {
                RegisterScreen(
                    onRegistered = { navController.popBackStack() },
                    onLoginClick = {
                        navController.navigate(LoginRoute) { popUpTo<RegisterRoute> { inclusive = true } }
                    },
                )
            }
            composable<InterestsRoute> {
                InterestsScreen(onSaved = { navController.popBackStack() })
            }
        }
    }
}
