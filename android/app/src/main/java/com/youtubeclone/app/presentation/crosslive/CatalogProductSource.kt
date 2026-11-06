package com.youtubeclone.app.presentation.crosslive

import com.youtubeclone.app.domain.model.LiveProduct
import com.youtubeclone.app.domain.repository.LivesRepository
import fr.echimine.live.ui.ProductSource
import fr.echimine.live.ui.ProductSuggestion
import java.text.Normalizer

/**
 * Source de produits du live partagé (ADR-031) : les produits de la chaîne de l'Animateur, ceux de
 * notre live shopping (GET /me/products, ADR-029). La recherche se fait dans l'application : le
 * serveur commun ne reçoit que le produit épinglé (nom, prix, devise, image), jamais notre
 * catalogue ni nos identifiants. Sans chaîne, la liste est vide.
 */
class CatalogProductSource(private val lives: LivesRepository) : ProductSource {
    override suspend fun searchProducts(query: String): List<ProductSuggestion> =
        matchingProducts(lives.getMyProducts(), query)
}

/** Produits dont le nom contient [query], sans tenir compte des majuscules ni des accents. */
internal fun matchingProducts(products: List<LiveProduct>, query: String): List<ProductSuggestion> {
    val needle = fold(query)
    return products.filter { fold(it.name).contains(needle) }.map { it.toSuggestion() }
}

internal fun LiveProduct.toSuggestion() = ProductSuggestion(
    id = id,
    name = name,
    priceCents = priceCents,
    // Nos devises sont en minuscules (« eur ») ; le serveur commun exige le code ISO en majuscules.
    currency = currency.uppercase(),
    // Le serveur commun refuse une image hors https : en développement (http), le produit part
    // sans image plutôt que d'être écarté.
    imageUrl = imageUrl.takeIf { it.startsWith("https://") },
)

private fun fold(text: String): String =
    Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
