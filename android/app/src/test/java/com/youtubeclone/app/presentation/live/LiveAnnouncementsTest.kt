package com.youtubeclone.app.presentation.live

import com.youtubeclone.app.domain.model.LiveAuthor
import com.youtubeclone.app.domain.model.LiveChatMessage
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Règles de regroupement des annonces TalkBack du live (ADR-030, D3 et D4).
class LiveAnnouncementsTest {

    private fun message(id: String, authorId: String = "u-$id", purchase: Boolean = false) = LiveChatMessage(
        id = id,
        liveId = "live",
        content = "texte $id",
        createdAt = Instant.EPOCH,
        author = LiveAuthor(authorId, "Auteur $id", null),
        isPurchase = purchase,
    )

    // Du plus récent au plus ancien, comme l'écran.
    private val history = listOf(message("3"), message("2"), message("1"))

    @Test
    fun `rien de nouveau n'annonce rien`() {
        val digest = digestNewMessages(history, lastSeenId = "3", myId = null)
        assertEquals(0, digest.count)
        assertNull(digest.single)
        assertTrue(digest.purchases.isEmpty())
    }

    @Test
    fun `un seul nouveau message est annoncé tel quel`() {
        val digest = digestNewMessages(listOf(message("4")) + history, lastSeenId = "3", myId = null)
        assertEquals(1, digest.count)
        assertEquals("4", digest.single?.id)
    }

    @Test
    fun `plusieurs nouveaux messages sont résumés par leur nombre`() {
        val digest = digestNewMessages(listOf(message("6"), message("5"), message("4")) + history, lastSeenId = "3", myId = null)
        assertEquals(3, digest.count)
        assertNull(digest.single)
    }

    @Test
    fun `les messages écrits par soi ne sont pas annoncés`() {
        val digest = digestNewMessages(listOf(message("5", authorId = "moi"), message("4")) + history, lastSeenId = "3", myId = "moi")
        assertEquals(1, digest.count)
        assertEquals("4", digest.single?.id)
    }

    @Test
    fun `une annonce d'achat est toujours annoncée, du plus ancien au plus récent`() {
        val fresh = listOf(message("5", purchase = true), message("4", purchase = true))
        val digest = digestNewMessages(fresh + history, lastSeenId = "3", myId = "moi")
        assertEquals(listOf("texte 4", "texte 5"), digest.purchases)
        assertEquals(0, digest.count)
    }

    @Test
    fun `une relecture après reconnexion n'annonce pas l'historique`() {
        val reloaded = listOf(message("9"), message("8"), message("7"))
        val digest = digestNewMessages(reloaded, lastSeenId = "3", myId = null)
        assertEquals(0, digest.count)
        assertTrue(digest.purchases.isEmpty())
    }

    @Test
    fun `à l'ouverture d'un chat vide, le premier message est nouveau`() {
        val digest = digestNewMessages(listOf(message("1")), lastSeenId = null, myId = null)
        assertEquals(1, digest.count)
    }

    @Test
    fun `un changement de spectateurs minuscule sur une grande salle n'est pas notable`() {
        assertFalse(isNotableViewerChange(announced = 200, current = 201))
        assertTrue(isNotableViewerChange(announced = 200, current = 220))
    }

    @Test
    fun `dans une petite salle, chaque changement compte`() {
        assertTrue(isNotableViewerChange(announced = 2, current = 3))
        assertTrue(isNotableViewerChange(announced = 0, current = 1))
        assertFalse(isNotableViewerChange(announced = 3, current = 3))
    }
}
