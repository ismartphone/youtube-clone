package com.youtubeclone.app.data.remote

import com.youtubeclone.app.data.remote.dto.LiveDto
import com.youtubeclone.app.data.remote.dto.LiveCheckoutDto
import com.youtubeclone.app.data.remote.dto.LiveListDto
import com.youtubeclone.app.data.remote.dto.LiveMessageDto
import com.youtubeclone.app.data.remote.dto.LiveMessageListDto
import com.youtubeclone.app.data.remote.dto.LiveMessageRequestDto
import com.youtubeclone.app.data.remote.dto.LiveProductDto
import com.youtubeclone.app.data.remote.dto.LiveProductListDto
import com.youtubeclone.app.data.remote.dto.LivePurchaseRequestDto
import com.youtubeclone.app.data.remote.dto.LiveReactionRequestDto
import com.youtubeclone.app.data.remote.dto.PinProductRequestDto
import com.youtubeclone.app.data.remote.dto.StartLiveRequestDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

// Lives (ADR-029) : la lecture est publique, démarrer, terminer et écrire exigent une session.
interface LivesApi {
    @POST("me/lives")
    suspend fun startLive(@Body body: StartLiveRequestDto): LiveDto

    @GET("lives")
    suspend fun getRunningLives(@Query("channelId") channelId: String?): LiveListDto

    @GET("lives/{id}")
    suspend fun getLive(@Path("id") id: String): LiveDto

    @POST("lives/{id}/end")
    suspend fun endLive(@Path("id") id: String): LiveDto

    @GET("lives/{id}/messages")
    suspend fun getMessages(@Path("id") id: String): LiveMessageListDto

    @POST("lives/{id}/messages")
    suspend fun sendMessage(@Path("id") id: String, @Body body: LiveMessageRequestDto): LiveMessageDto

    // Réponse 204 : la réaction n'est ni stockée ni renvoyée, elle n'arrive que par la salle.
    @POST("lives/{id}/reactions")
    suspend fun sendReaction(@Path("id") id: String, @Body body: LiveReactionRequestDto)

    // Produit épinglé et achat (ADR-029, étape 3).
    @GET("me/products")
    suspend fun getMyProducts(): LiveProductListDto

    @PUT("lives/{id}/pinned-product")
    suspend fun pinProduct(@Path("id") id: String, @Body body: PinProductRequestDto): LiveProductDto

    @DELETE("lives/{id}/pinned-product")
    suspend fun unpinProduct(@Path("id") id: String)

    @POST("lives/{id}/checkout")
    suspend fun checkout(@Path("id") id: String): LiveCheckoutDto

    // Réponse sans montant : l'annonce dans le chat arrive par la salle.
    @POST("lives/{id}/purchases")
    suspend fun confirmPurchase(@Path("id") id: String, @Body body: LivePurchaseRequestDto)
}
