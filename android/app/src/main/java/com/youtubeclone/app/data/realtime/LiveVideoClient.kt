package com.youtubeclone.app.data.realtime

import com.youtubeclone.app.data.remote.dto.CommentDto
import com.youtubeclone.app.data.remote.dto.toDomain
import com.youtubeclone.app.domain.model.LiveVideoEvent
import com.youtubeclone.app.domain.repository.LiveVideoRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

/** Événements en direct de la vidéo regardée (ADR-004, ADR-010), sur la salle de la vidéo. */
@Singleton
class LiveVideoClient @Inject constructor(
    private val socket: RealtimeSocket,
    private val json: Json,
) : LiveVideoRepository {

    override fun videoEvents(videoId: String): Flow<LiveVideoEvent> =
        socket.events("videoId" to videoId, LiveVideoEvent.Connected) { event, data -> parse(event, data, videoId) }

    private fun parse(event: String, data: JsonObject, videoId: String): LiveVideoEvent? = when (event) {
        "comment:new" -> LiveVideoEvent.CommentAdded(json.decodeFromJsonElement(CommentDto.serializer(), data).toDomain())
        "comment:reply" -> LiveVideoEvent.ReplyAdded(json.decodeFromJsonElement(CommentDto.serializer(), data).toDomain())
        "views:update" -> data.forVideo(videoId)?.let { LiveVideoEvent.ViewsUpdated(it.getValue("viewCount").jsonPrimitive.int) }
        "likes:update" -> data.forVideo(videoId)?.let { LiveVideoEvent.LikesUpdated(it.getValue("likeCount").jsonPrimitive.int) }
        else -> null
    }

    private fun JsonObject.forVideo(videoId: String): JsonObject? =
        takeIf { getValue("videoId").jsonPrimitive.content == videoId }
}
