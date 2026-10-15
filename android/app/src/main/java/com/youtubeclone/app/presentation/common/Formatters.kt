package com.youtubeclone.app.presentation.common

import java.util.Locale

/** Durée au format YouTube : 4:05, ou 1:02:30 au-delà d'une heure. */
fun formatDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

/** Nombre de vues abrégé en français : 0 vue, 1 vue, 950 vues, 1,2 k vues, 3,4 M de vues. */
fun formatViewCount(count: Int): String = when {
    count < 2 -> "$count vue"
    count < 1_000 -> "$count vues"
    count < 1_000_000 -> "${abbreviate(count / 1_000.0)} k vues"
    else -> "${abbreviate(count / 1_000_000.0)} M de vues"
}

// Une décimale au plus, sans ",0" inutile : 1,2 mais 3 plutôt que 3,0.
private fun abbreviate(value: Double): String {
    val rounded = String.format(Locale.FRANCE, "%.1f", value)
    return rounded.removeSuffix(",0")
}
