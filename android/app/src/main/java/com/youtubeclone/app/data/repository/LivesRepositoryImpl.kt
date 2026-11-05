package com.youtubeclone.app.data.repository

import com.youtubeclone.app.data.realtime.RealtimeSocket
import com.youtubeclone.app.data.remote.LivesApi
import com.youtubeclone.app.data.remote.apiCall
import com.youtubeclone.app.data.remote.dto.LiveMessageDto
import com.youtubeclone.app.data.remote.dto.LiveMessageRequestDto
import com.youtubeclone.app.data.remote.dto.LivePinnedDto
import com.youtubeclone.app.data.remote.dto.LivePurchaseRequestDto
import com.youtubeclone.app.data.remote.dto.LiveReactionDto
import com.youtubeclone.app.data.remote.dto.LiveReactionRequestDto
import com.youtubeclone.app.data.remote.dto.LiveViewersDto
import com.youtubeclone.app.data.remote.dto.PinProductRequestDto
import com.youtubeclone.app.data.remote.dto.StartLiveRequestDto
import com.youtubeclone.app.data.remote.dto.toDomain
import com.youtubeclone.app.domain.model.Live
import com.youtubeclone.app.domain.model.LiveChatMessage
import com.youtubeclone.app.domain.model.LiveCheckout
import com.youtubeclone.app.domain.model.LiveProduct
import com.youtubeclone.app.domain.model.LiveRoomEvent
import com.youtubeclone.app.domain.repository.LivesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class LivesRepositoryImpl @Inject constructor(
    private val api: LivesApi,
    private val socket: RealtimeSocket,
    private val json: Json,
) : LivesRepository {

    // apiCall garde le message du serveur (seuil d'abonnés, vidéo refusée...) pour l'afficher.
    override suspend fun startLive(title: String, videoId: String): Live =
        apiCall(json) { api.startLive(StartLiveRequestDto(title, videoId)) }.toDomain()

    override suspend fun getLive(liveId: String): Live = apiCall(json) { api.getLive(liveId) }.toDomain()

    override suspend fun getRunningLives(channelId: String?): List<Live> =
        apiCall(json) { api.getRunningLives(channelId) }.items.map { it.toDomain() }

    override suspend fun endLive(liveId: String): Live = apiCall(json) { api.endLive(liveId) }.toDomain()

    override suspend fun getMessages(liveId: String): List<LiveChatMessage> =
        apiCall(json) { api.getMessages(liveId) }.items.map { it.toDomain() }

    override suspend fun sendMessage(liveId: String, content: String): LiveChatMessage =
        apiCall(json) { api.sendMessage(liveId, LiveMessageRequestDto(content)) }.toDomain()

    override suspend fun sendReaction(liveId: String, emoji: String) {
        apiCall(json) { api.sendReaction(liveId, LiveReactionRequestDto(emoji)) }
    }

    override suspend fun getMyProducts(): List<LiveProduct> =
        apiCall(json) { api.getMyProducts() }.items.map { it.toDomain() }

    override suspend fun pinProduct(liveId: String, productId: String): LiveProduct =
        apiCall(json) { api.pinProduct(liveId, PinProductRequestDto(productId)) }.toDomain()

    override suspend fun unpinProduct(liveId: String) {
        apiCall(json) { api.unpinProduct(liveId) }
    }

    override suspend fun startCheckout(liveId: String): LiveCheckout =
        apiCall(json) { api.checkout(liveId) }.toDomain()

    override suspend fun confirmPurchase(liveId: String, purchaseToken: String) {
        apiCall(json) { api.confirmPurchase(liveId, LivePurchaseRequestDto(purchaseToken)) }
    }

    override fun liveEvents(liveId: String): Flow<LiveRoomEvent> =
        socket.events("liveId" to liveId, LiveRoomEvent.Connected, ::parse)

    private fun parse(event: String, data: JsonObject): LiveRoomEvent? = when (event) {
        "live:message" -> LiveRoomEvent.MessageReceived(json.decodeFromJsonElement(LiveMessageDto.serializer(), data).toDomain())
        "live:viewers" -> LiveRoomEvent.ViewersChanged(json.decodeFromJsonElement(LiveViewersDto.serializer(), data).viewerCount)
        "live:reaction" -> LiveRoomEvent.ReactionReceived(json.decodeFromJsonElement(LiveReactionDto.serializer(), data).emoji)
        "live:pinned" -> LiveRoomEvent.ProductPinned(json.decodeFromJsonElement(LivePinnedDto.serializer(), data).product?.toDomain())
        "live:ended" -> LiveRoomEvent.Ended
        else -> null
    }
}
