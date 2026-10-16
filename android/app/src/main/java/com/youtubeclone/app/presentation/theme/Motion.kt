package com.youtubeclone.app.presentation.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** Durées et courbe communes : courtes, pour que l'application paraisse vive. */
object Motion {
    /** Ouverture et fermeture d'un écran (glissement et fondu). */
    const val SCREEN_MS = 280

    /** Changement d'onglet, changements d'état (couleurs, compteurs). */
    const val FADE_MS = 250

    /** Micro-animations : bouton, icône. */
    const val SHORT_MS = 150

    /** Seul effet gardé quand l'utilisateur a demandé moins d'animations. */
    const val REDUCED_FADE_MS = 120

    /** Accélération puis décélération. */
    val Easing: Easing = FastOutSlowInEasing
}

/**
 * Vrai si l'utilisateur a demandé de réduire les animations (réglage « Supprimer les animations »
 * d'Android, qui met l'échelle de durée des animations à 0). Les transitions deviennent alors
 * de simples fondus courts, et les mouvements décoratifs sont supprimés.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Lit le réglage système et suit ses changements tant que l'écran est affiché. */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    var reduceMotion by remember { mutableStateOf(readReduceMotion(context)) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduceMotion = readReduceMotion(context)
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return reduceMotion
}

private fun readReduceMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
