package com.youtubeclone.app.presentation.live

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.LiveChatMessage
import kotlinx.coroutines.delay

// Annonces TalkBack de l'écran du live (ADR-030, D3 et D4). Le chat et le nombre de spectateurs
// changent sans que le focus y soit : un seul nœud invisible, en région vivante polie, annonce un
// résumé regroupé, au plus une fois par fenêtre. Ni la liste du chat ni la pastille ne sont des
// régions vivantes, sinon TalkBack annoncerait chaque événement.

/** Fenêtre de regroupement du chat : au plus une annonce toutes les 5 secondes (valeur de départ, D3). */
internal const val CHAT_ANNOUNCE_WINDOW_MS = 5_000L

/** Le nombre de spectateurs est annoncé au plus toutes les 30 secondes (valeur de départ, D3). */
internal const val VIEWERS_ANNOUNCE_WINDOW_MS = 30_000L

/** Ce que contiennent les nouveaux messages, prêt à être annoncé. */
internal data class ChatDigest(
    /** Annonces d'achat écrites par le serveur : toujours annoncées, une par une. */
    val purchases: List<String>,
    /** Le message seul, quand le rythme est lent. */
    val single: LiveChatMessage?,
    /** Nombre de messages quand il y en a plusieurs : « N nouveaux messages ». */
    val count: Int,
)

/**
 * Messages arrivés depuis [lastSeenId], du plus récent au plus ancien. Les messages de [myId] ne
 * sont pas annoncés (on vient de les écrire). Un [lastSeenId] absent de la liste (relecture après
 * une reconnexion) n'annonce rien : on ne lit pas l'historique.
 */
internal fun digestNewMessages(messages: List<LiveChatMessage>, lastSeenId: String?, myId: String?): ChatDigest {
    val fresh = when {
        lastSeenId == null -> messages
        messages.none { it.id == lastSeenId } -> emptyList()
        else -> messages.takeWhile { it.id != lastSeenId }
    }
    val purchases = fresh.filter { it.isPurchase }.reversed().map { it.content }
    val chat = fresh.filter { !it.isPurchase && (myId == null || it.author.id != myId) }
    return ChatDigest(
        purchases = purchases,
        single = chat.singleOrNull(),
        count = chat.size,
    )
}

/** Vrai si le nombre de spectateurs a changé de façon notable : au moins un, et 10 % de la dernière valeur annoncée. */
internal fun isNotableViewerChange(announced: Int, current: Int): Boolean {
    val delta = kotlin.math.abs(current - announced)
    return delta >= 1 && delta * 10 >= announced
}

/**
 * Nœud d'annonces du live, à poser dans une zone de l'écran. Inactif tant que TalkBack (ou un
 * autre service d'exploration au toucher) est éteint : aucun effet pour les autres utilisateurs.
 */
@Composable
fun LiveAnnouncements(
    messages: List<LiveChatMessage>,
    myId: String?,
    viewerCount: Int,
    isEnded: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var announcement by remember { mutableStateOf("") }
    // Deux annonces identiques de suite ne changeraient pas le texte : l'espace insécable de fin
    // (invisible à la voix) alterne pour que TalkBack les lise toutes les deux.
    var toggle by remember { mutableStateOf(false) }
    val latestMessages by rememberUpdatedState(messages)
    val latestViewers by rememberUpdatedState(viewerCount)
    val latestMyId by rememberUpdatedState(myId)

    val endedText = stringResource(R.string.live_ended)

    fun publish(text: String) {
        toggle = !toggle
        announcement = if (toggle) text else "$text "
    }

    LaunchedEffect(isEnded) {
        if (isEnded) publish(endedText)
    }

    LaunchedEffect(Unit) {
        var lastSeenId = latestMessages.firstOrNull()?.id
        var announcedViewers = latestViewers
        var lastViewersAt = 0L
        var elapsed = 0L
        while (true) {
            delay(CHAT_ANNOUNCE_WINDOW_MS)
            elapsed += CHAT_ANNOUNCE_WINDOW_MS
            val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            val active = manager?.isEnabled == true && manager.isTouchExplorationEnabled
            val current = latestMessages
            val digest = digestNewMessages(current, lastSeenId, latestMyId)
            lastSeenId = current.firstOrNull()?.id ?: lastSeenId
            if (!active) {
                announcedViewers = latestViewers
                continue
            }
            val parts = mutableListOf<String>()
            parts += digest.purchases
            when {
                digest.single != null -> parts += context.getString(
                    R.string.live_announce_message,
                    digest.single.author.displayName,
                    digest.single.content,
                )
                digest.count > 1 -> parts += context.resources.getQuantityString(R.plurals.live_announce_new_messages, digest.count, digest.count)
            }
            val viewers = latestViewers
            if (elapsed - lastViewersAt >= VIEWERS_ANNOUNCE_WINDOW_MS && isNotableViewerChange(announcedViewers, viewers)) {
                parts += context.resources.getQuantityString(R.plurals.live_viewers, viewers, viewers)
                announcedViewers = viewers
                lastViewersAt = elapsed
            }
            if (parts.isNotEmpty()) publish(parts.joinToString(". "))
        }
    }

    Box(
        modifier
            .size(1.dp)
            .semantics {
                if (announcement.isNotEmpty()) {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = announcement
                }
            },
    )
}
