package com.youtubeclone.app.presentation.common

import org.junit.Assert.assertEquals
import org.junit.Test

// Durée dite à voix haute par TalkBack (ADR-030) : « 4:05 » ne doit pas être lu comme une heure.
class FormattersTest {

    @Test
    fun `une durée en secondes seulement`() {
        assertEquals("1 seconde", formatSpokenDuration(1))
        assertEquals("45 secondes", formatSpokenDuration(45))
    }

    @Test
    fun `des minutes et des secondes`() {
        assertEquals("4 minutes et 5 secondes", formatSpokenDuration(245))
        assertEquals("1 minute et 1 seconde", formatSpokenDuration(61))
    }

    @Test
    fun `des minutes pleines n'ont pas de secondes`() {
        assertEquals("5 minutes", formatSpokenDuration(300))
    }

    @Test
    fun `des heures, des minutes et des secondes`() {
        assertEquals("1 heure, 2 minutes et 30 secondes", formatSpokenDuration(3750))
        assertEquals("2 heures", formatSpokenDuration(7200))
    }

    @Test
    fun `zéro seconde reste dit`() {
        assertEquals("0 seconde", formatSpokenDuration(0))
    }
}
