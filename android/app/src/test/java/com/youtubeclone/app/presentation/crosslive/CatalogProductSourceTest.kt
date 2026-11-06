package com.youtubeclone.app.presentation.crosslive

import com.youtubeclone.app.domain.model.LiveProduct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogProductSourceTest {

    private fun product(name: String, imageUrl: String = "https://cdn.example.com/p.png") =
        LiveProduct(id = "id-$name", name = name, priceCents = 2490, currency = "eur", imageUrl = imageUrl, description = "")

    @Test
    fun `cherche sans tenir compte des majuscules ni des accents`() {
        val products = listOf(product("Écouteurs sans fil"), product("Casquette"))
        assertEquals(listOf("Écouteurs sans fil"), matchingProducts(products, "ECOUT").map { it.name })
        assertEquals(2, matchingProducts(products, "  ").size)
    }

    @Test
    fun `transmet la devise en majuscules et le prix en centimes`() {
        val suggestion = product("Casquette").toSuggestion()
        assertEquals("EUR", suggestion.currency)
        assertEquals(2490, suggestion.priceCents)
        assertEquals("id-Casquette", suggestion.id)
    }

    @Test
    fun `omet une image qui n'est pas en https`() {
        assertNull(product("Casquette", imageUrl = "http://10.0.2.2:3000/media/p.png").toSuggestion().imageUrl)
        assertEquals("https://cdn.example.com/p.png", product("Casquette").toSuggestion().imageUrl)
    }
}
