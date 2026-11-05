package com.youtubeclone.app.domain.repository

import com.youtubeclone.app.domain.model.Live
import com.youtubeclone.app.domain.model.LiveChatMessage
import com.youtubeclone.app.domain.model.LiveCheckout
import com.youtubeclone.app.domain.model.LiveProduct
import com.youtubeclone.app.domain.model.LiveRoomEvent
import kotlinx.coroutines.flow.Flow

/** Accès aux lives (ADR-029). Les implémentations lèvent une [com.youtubeclone.app.domain.model.DataError] en cas d'échec. */
interface LivesRepository {
    /** Démarre un live sur la chaîne de l'utilisateur, avec [videoId] comme flux. Refus du serveur : message affichable. */
    suspend fun startLive(title: String, videoId: String): Live

    /** État du live, en cours ou terminé. */
    suspend fun getLive(liveId: String): Live

    /** Lives en cours, publics ; ceux de [channelId] seulement s'il est donné. */
    suspend fun getRunningLives(channelId: String? = null): List<Live>

    /** Termine le live (diffuseur seulement). */
    suspend fun endLive(liveId: String): Live

    /** Derniers messages du chat, du plus récent au plus ancien. */
    suspend fun getMessages(liveId: String): List<LiveChatMessage>

    /** Écrit dans le chat ; le serveur diffuse ensuite le message à la salle, auteur compris. */
    suspend fun sendMessage(liveId: String, content: String): LiveChatMessage

    /** Envoie une réaction à la salle (compte connecté) ; elle revient par [liveEvents], pour tous, auteur compris. */
    suspend fun sendReaction(liveId: String, emoji: String)

    /** Produits de la chaîne du diffuseur, parmi lesquels il choisit celui à épingler. */
    suspend fun getMyProducts(): List<LiveProduct>

    /** Épingle [productId] : la carte apparaît chez tous les spectateurs (diffuseur seulement). */
    suspend fun pinProduct(liveId: String, productId: String): LiveProduct

    /** Retire le produit épinglé (diffuseur seulement). */
    suspend fun unpinProduct(liveId: String)

    /** Étape 1 de l'achat : le serveur démarre le paiement du produit épinglé, à son prix. */
    suspend fun startCheckout(liveId: String): LiveCheckout

    /** Étape 2 : le serveur vérifie le paiement [purchaseToken] et enregistre l'achat ; l'annonce arrive par [liveEvents]. */
    suspend fun confirmPurchase(liveId: String, purchaseToken: String)

    /** Événements de la salle du live, tant que le flux est collecté ; sa fermeture quitte la salle. */
    fun liveEvents(liveId: String): Flow<LiveRoomEvent>
}
