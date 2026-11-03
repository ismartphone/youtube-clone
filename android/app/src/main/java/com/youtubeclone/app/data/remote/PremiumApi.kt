package com.youtubeclone.app.data.remote

import com.youtubeclone.app.data.remote.dto.CheckoutDto
import com.youtubeclone.app.data.remote.dto.PremiumStatusDto
import com.youtubeclone.app.data.remote.dto.SubscribeRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST

// Achat simulé par notre backend (ADR-012). Routes protégées : le token est ajouté par AuthInterceptor.
interface PremiumApi {
    @GET("me/premium")
    suspend fun getStatus(): PremiumStatusDto

    /** Étape 1 : le serveur émet un jeton d'achat valable quelques minutes. */
    @POST("me/premium/checkout")
    suspend fun checkout(): CheckoutDto

    /** Étape 2 : le serveur vérifie le jeton et active l'abonnement pour un mois. */
    @POST("me/premium/subscribe")
    suspend fun subscribe(@Body body: SubscribeRequest): PremiumStatusDto

    /** Fin immédiate de l'abonnement en cours. */
    @DELETE("me/premium")
    suspend fun cancel(): PremiumStatusDto
}
