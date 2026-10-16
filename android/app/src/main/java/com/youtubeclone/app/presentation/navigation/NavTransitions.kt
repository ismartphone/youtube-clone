package com.youtubeclone.app.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import com.youtubeclone.app.presentation.theme.Motion
import kotlin.reflect.KClass

// Transitions entre écrans :
// - d'un onglet à un autre : fondu simple, sans glissement ;
// - ouverture d'un écran (lecteur, chaîne, connexion...) : glissement depuis la droite avec fondu,
//   l'écran quitté recule légèrement ; retour : le mouvement inverse ;
// - animations réduites (réglage système) : fondu court dans tous les cas.

private typealias Scope = AnimatedContentTransitionScope<NavBackStackEntry>

private fun <T> screenSpec() = tween<T>(Motion.SCREEN_MS, easing = Motion.Easing)
private fun <T> tabSpec() = tween<T>(Motion.FADE_MS, easing = Motion.Easing)
private fun <T> reducedSpec() = tween<T>(Motion.REDUCED_FADE_MS)

// L'écran quitté ne recule que d'un quart de sa largeur : il reste derrière le nouvel écran.
private const val BACKGROUND_SHIFT = 4

internal class NavTransitions(
    private val tabRoutes: List<KClass<*>>,
    private val reduceMotion: Boolean,
) {
    private fun NavBackStackEntry.isTab() = tabRoutes.any { destination.hasRoute(it) }
    private fun Scope.betweenTabs() = initialState.isTab() && targetState.isTab()

    val enter: Scope.() -> EnterTransition = {
        when {
            reduceMotion -> fadeIn(reducedSpec())
            betweenTabs() -> fadeIn(tabSpec())
            else -> slideIntoContainer(SlideDirection.Start, screenSpec()) + fadeIn(screenSpec())
        }
    }

    val exit: Scope.() -> ExitTransition = {
        when {
            reduceMotion -> fadeOut(reducedSpec())
            betweenTabs() -> fadeOut(tabSpec())
            else -> slideOutOfContainer(SlideDirection.Start, screenSpec()) { it / BACKGROUND_SHIFT } +
                fadeOut(screenSpec())
        }
    }

    val popEnter: Scope.() -> EnterTransition = {
        when {
            reduceMotion -> fadeIn(reducedSpec())
            betweenTabs() -> fadeIn(tabSpec())
            else -> slideIntoContainer(SlideDirection.End, screenSpec()) { it / BACKGROUND_SHIFT } +
                fadeIn(screenSpec())
        }
    }

    val popExit: Scope.() -> ExitTransition = {
        when {
            reduceMotion -> fadeOut(reducedSpec())
            betweenTabs() -> fadeOut(tabSpec())
            else -> slideOutOfContainer(SlideDirection.End, screenSpec()) + fadeOut(screenSpec())
        }
    }
}
