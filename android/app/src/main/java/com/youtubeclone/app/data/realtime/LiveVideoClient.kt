package com.youtubeclone.app.data.realtime

import com.youtubeclone.app.BuildConfig
import com.youtubeclone.app.data.local.TokenStore
import com.youtubeclone.app.data.remote.TokenAuthenticator
import com.youtubeclone.app.data.remote.dto.CommentDto
import com.youtubeclone.app.data.remote.dto.toDomain
import com.youtubeclone.app.domain.model.LiveVideoEvent
import com.youtubeclone.app.domain.repository.LiveVideoRepository
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Connexion temps réel à la passerelle WebSocket du backend (ADR-004).
 *
 * Elle passe par le client OkHttp de l'application : AuthInterceptor ajoute l'access token
 * à la requête de handshake. Sans session, la connexion se fait en visiteur (lecture seule).
 * Messages échangés : { "event": ..., "data": ... }.
 */
@Singleton
class LiveVideoClient @Inject constructor(
    okHttpClient: OkHttpClient,
    private val tokenStore: TokenStore,
    private val tokenAuthenticator: TokenAuthenticator,
    private val json: Json,
) : LiveVideoRepository {

    // Un ping régulier détecte une connexion morte (réseau coupé sans fermeture propre).
    private val client = okHttpClient.newBuilder()
        .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
        .build()

    // OkHttp accepte une URL http(s) pour un WebSocket et la convertit en ws(s).
    private val url = BuildConfig.API_BASE_URL + "realtime"

    override fun videoEvents(videoId: String): Flow<LiveVideoEvent> = channelFlow {
        var retryDelayMs = INITIAL_RETRY_DELAY_MS
        // Tant que le flux est collecté : connexion, puis reconnexion après chaque coupure.
        while (isActive) {
            val sentToken = tokenStore.get()?.accessToken
            val closeCode = connectOnce(videoId, channel) { retryDelayMs = INITIAL_RETRY_DELAY_MS }
            if (closeCode == INVALID_TOKEN_CLOSE_CODE && sentToken != null) {
                // Token expiré : on le renouvelle puis on se reconnecte tout de suite. Si le
                // renouvellement échoue, la session est effacée et la reconnexion se fait en visiteur.
                withContext(Dispatchers.IO) { tokenAuthenticator.renew(sentToken) }
                continue
            }
            delay(retryDelayMs)
            retryDelayMs = min(retryDelayMs * 2, MAX_RETRY_DELAY_MS)
        }
    }

    /** Ouvre une connexion et attend sa fin. Renvoie le code de fermeture, ou null en cas d'échec réseau. */
    private suspend fun connectOnce(
        videoId: String,
        events: SendChannel<LiveVideoEvent>,
        onOpen: () -> Unit,
    ): Int? = suspendCancellableCoroutine { continuation ->
        val finished = AtomicBoolean(false)
        fun finish(code: Int?) {
            if (finished.compareAndSet(false, true)) continuation.resume(code)
        }

        val socket = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(message("join", videoId))
                    onOpen()
                    events.trySend(LiveVideoEvent.Connected)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    parse(text, videoId)?.let { events.trySend(it) }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(NORMAL_CLOSURE, null)
                    finish(code)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = finish(code)

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = finish(null)
            },
        )
        // Écran quitté : fermeture propre, la boucle de reconnexion s'arrête avec la collecte.
        continuation.invokeOnCancellation { socket.close(NORMAL_CLOSURE, null) }
    }

    private fun message(event: String, videoId: String): String =
        buildJsonObject {
            put("event", event)
            put("data", buildJsonObject { put("videoId", videoId) })
        }.toString()

    // Un message illisible ou inconnu est ignoré : il ne doit pas couper la connexion.
    private fun parse(text: String, videoId: String): LiveVideoEvent? = runCatching {
        val root = json.parseToJsonElement(text).jsonObject
        val data = root.getValue("data").jsonObject
        when (root.getValue("event").jsonPrimitive.content) {
            "comment:new" -> LiveVideoEvent.CommentAdded(json.decodeFromJsonElement(CommentDto.serializer(), data).toDomain())
            "comment:reply" -> LiveVideoEvent.ReplyAdded(json.decodeFromJsonElement(CommentDto.serializer(), data).toDomain())
            "views:update" -> data.forVideo(videoId)?.let { LiveVideoEvent.ViewsUpdated(it.getValue("viewCount").jsonPrimitive.int) }
            "likes:update" -> data.forVideo(videoId)?.let { LiveVideoEvent.LikesUpdated(it.getValue("likeCount").jsonPrimitive.int) }
            else -> null
        }
    }.getOrNull()

    private fun JsonObject.forVideo(videoId: String): JsonObject? =
        takeIf { getValue("videoId").jsonPrimitive.content == videoId }

    private companion object {
        const val INVALID_TOKEN_CLOSE_CODE = 4401
        const val NORMAL_CLOSURE = 1000
        const val PING_INTERVAL_SECONDS = 20L
        const val INITIAL_RETRY_DELAY_MS = 1_000L
        const val MAX_RETRY_DELAY_MS = 30_000L
    }
}
