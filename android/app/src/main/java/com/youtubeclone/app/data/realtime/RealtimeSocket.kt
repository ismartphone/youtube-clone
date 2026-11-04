package com.youtubeclone.app.data.realtime

import com.youtubeclone.app.BuildConfig
import com.youtubeclone.app.data.local.TokenStore
import com.youtubeclone.app.data.remote.TokenAuthenticator
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Connexion temps réel à la passerelle WebSocket du backend (ADR-004), pour une salle : une vidéo
 * (ADR-010), une conversation ou la salle personnelle de l'utilisateur (ADR-025).
 *
 * Elle passe par le client OkHttp de l'application : AuthInterceptor ajoute l'access token
 * à la requête de handshake. Sans session, la connexion se fait en visiteur (lecture seule).
 * Messages échangés : { "event": ..., "data": ... }.
 */
@Singleton
class RealtimeSocket @Inject constructor(
    okHttpClient: OkHttpClient,
    private val tokenStore: TokenStore,
    private val tokenAuthenticator: TokenAuthenticator,
    private val json: Json,
) {
    // Un ping régulier détecte une connexion morte (réseau coupé sans fermeture propre).
    private val client = okHttpClient.newBuilder()
        .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
        .build()

    // OkHttp accepte une URL http(s) pour un WebSocket et la convertit en ws(s).
    private val url = BuildConfig.API_BASE_URL + "realtime"

    /**
     * Événements de la salle [room] (par exemple "videoId" to l'identifiant), tant que le flux est
     * collecté. [connected] est émis à chaque (re)connexion ; [parse] traduit un message, ou
     * renvoie null pour l'ignorer.
     */
    fun <T : Any> events(
        room: Pair<String, String>,
        connected: T,
        parse: (event: String, data: JsonObject) -> T?,
    ): Flow<T> = channelFlow {
        var retryDelayMs = INITIAL_RETRY_DELAY_MS
        // Tant que le flux est collecté : connexion, puis reconnexion après chaque coupure.
        while (isActive) {
            val sentToken = tokenStore.get()?.accessToken
            val closeCode = connectOnce(room, channel, connected, parse) { retryDelayMs = INITIAL_RETRY_DELAY_MS }
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
    private suspend fun <T : Any> connectOnce(
        room: Pair<String, String>,
        events: SendChannel<T>,
        connected: T,
        parse: (String, JsonObject) -> T?,
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
                    webSocket.send(join(room))
                    onOpen()
                    events.trySend(connected)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    // Un message illisible ou inconnu est ignoré : il ne doit pas couper la connexion.
                    runCatching {
                        val root = json.parseToJsonElement(text).jsonObject
                        parse(root.getValue("event").jsonPrimitive.content, root.getValue("data").jsonObject)
                    }.getOrNull()?.let { events.trySend(it) }
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

    private fun join(room: Pair<String, String>): String =
        buildJsonObject {
            put("event", "join")
            put("data", buildJsonObject { put(room.first, room.second) })
        }.toString()

    private companion object {
        const val INVALID_TOKEN_CLOSE_CODE = 4401
        const val NORMAL_CLOSURE = 1000
        const val PING_INTERVAL_SECONDS = 20L
        const val INITIAL_RETRY_DELAY_MS = 1_000L
        const val MAX_RETRY_DELAY_MS = 30_000L
    }
}
