package com.youtubeclone.app.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.youtubeclone.app.presentation.theme.YoutubeCloneTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Filet de non-régression de l'accessibilité (ADR-030) : une carte vidéo est UN arrêt pour TalkBack.
@RunWith(AndroidJUnit4::class)
class VideoCardSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setCard() = rule.setContent {
        YoutubeCloneTheme {
            VideoCard(
                video = previewVideo("1", "Le ballet des méduses", "Studio Libre", 1_234, 3),
                onChannelClick = {},
                modifier = Modifier.clickable {},
            )
        }
    }

    @Test
    fun laCarteSeLitEnUnePhrase() {
        setCard()
        rule.onNodeWithContentDescription("Le ballet des méduses, chaîne Studio Libre", substring = true)
            .assertHasClickAction()
        // La durée est dite à voix haute, pas « 4:05 ».
        rule.onNodeWithContentDescription("durée 1 minute et 26 secondes", substring = true).assertExists()
    }

    @Test
    fun laCarteEstUnSeulArretCliquable() {
        setCard()
        // Ni l'avatar ni le nom de la chaîne ne sont des arrêts de plus.
        rule.onAllNodes(hasClickAction()).assertCountEquals(1)
    }

    @Test
    fun l_accesALaChaineEstUneActionPersonnalisee() {
        setCard()
        val node = rule.onNodeWithContentDescription("Le ballet des méduses", substring = true).fetchSemanticsNode()
        val actions = node.config[SemanticsActions.CustomActions]
        assertTrue(actions.any { it.label.contains("Studio Libre") })
    }
}
