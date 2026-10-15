package com.youtubeclone.app.presentation.common

import java.time.Duration
import java.time.Instant
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

/** Nombre d'abonnés abrégé en français : 0 abonné, 1 abonné, 950 abonnés, 1,2 k abonnés, 3,4 M d'abonnés. */
fun formatFollowerCount(count: Int): String = when {
    count < 2 -> "$count abonné"
    count < 1_000 -> "$count abonnés"
    count < 1_000_000 -> "${abbreviate(count / 1_000.0)} k abonnés"
    else -> "${abbreviate(count / 1_000_000.0)} M d'abonnés"
}

/** Nombre seul abrégé en français, pour un bouton : 950, 1,2 k, 3,4 M. */
fun formatCompactCount(count: Int): String = when {
    count < 1_000 -> "$count"
    count < 1_000_000 -> "${abbreviate(count / 1_000.0)} k"
    else -> "${abbreviate(count / 1_000_000.0)} M"
}

// Une décimale au plus, sans ",0" inutile : 1,2 mais 3 plutôt que 3,0.
private fun abbreviate(value: Double): String {
    val rounded = String.format(Locale.FRANCE, "%.1f", value)
    return rounded.removeSuffix(",0")
}

/** Date relative en français, comme sur YouTube : « il y a 3 jours », « il y a 2 mois ». */
fun formatRelativeDate(date: Instant, now: Instant = Instant.now()): String {
    val elapsed = Duration.between(date, now)
    val days = elapsed.toDays()
    return when {
        elapsed.toMinutes() < 1 -> "à l'instant"
        elapsed.toHours() < 1 -> ago(elapsed.toMinutes(), "minute", "minutes")
        days < 1 -> ago(elapsed.toHours(), "heure", "heures")
        days < 7 -> ago(days, "jour", "jours")
        days < 30 -> ago(days / 7, "semaine", "semaines")
        days < 365 -> ago(days / 30, "mois", "mois")
        else -> ago(days / 365, "an", "ans")
    }
}

private fun ago(count: Long, singular: String, plural: String) =
    "il y a $count ${if (count < 2) singular else plural}"
