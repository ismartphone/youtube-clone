package com.youtubeclone.app.data.repository

import android.content.Context
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.youtubeclone.app.data.remote.ChannelApi
import com.youtubeclone.app.data.remote.ContentUriRequestBody
import com.youtubeclone.app.data.remote.apiCall
import com.youtubeclone.app.data.remote.dto.ChannelProfileDto
import com.youtubeclone.app.data.remote.dto.CreateChannelRequestDto
import com.youtubeclone.app.data.remote.dto.NotificationLevelDto
import com.youtubeclone.app.data.remote.dto.apiValue
import com.youtubeclone.app.data.remote.dto.notificationLevelOf
import com.youtubeclone.app.data.remote.dto.UpdateChannelRequestDto
import com.youtubeclone.app.data.remote.dto.toDomain
import com.youtubeclone.app.domain.model.Channel
import com.youtubeclone.app.domain.model.ChannelImageKind
import com.youtubeclone.app.domain.model.ChannelPage
import com.youtubeclone.app.domain.model.ChannelProfile
import com.youtubeclone.app.domain.model.FollowChange
import com.youtubeclone.app.domain.model.FollowStatus
import com.youtubeclone.app.domain.model.NotificationLevel
import com.youtubeclone.app.domain.repository.ChannelRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody

class ChannelRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ChannelApi,
    private val json: Json,
) : ChannelRepository {

    // Instance unique (voir RepositoryModule) : tous les écrans reçoivent les mêmes changements.
    private val changes = MutableSharedFlow<FollowChange>(extraBufferCapacity = 16)
    override val followChanges: Flow<FollowChange> = changes.asSharedFlow()

    private val created = MutableSharedFlow<Channel>(extraBufferCapacity = 1)
    override val ownChannelCreated: Flow<Channel> = created.asSharedFlow()

    override suspend fun createChannel(name: String, handle: String, description: String?): Channel {
        val channel = apiCall(json) {
            api.createChannel(CreateChannelRequestDto(name = name, handle = handle, description = description))
        }.toDomain()
        created.emit(channel)
        return channel
    }

    override suspend fun getChannelPage(channelId: String): ChannelPage =
        apiCall(json) { api.getChannel(channelId) }.toDomain()

    override suspend fun getOwnChannel(): Channel? =
        apiCall(json) { api.getOwnChannel() }.channel?.toDomain()

    private val updated = MutableSharedFlow<ChannelProfile>(extraBufferCapacity = 1)
    override val ownChannelUpdated: Flow<ChannelProfile> = updated.asSharedFlow()

    override suspend fun updateOwnChannel(name: String?, handle: String?, description: String?): ChannelProfile =
        announce { api.updateOwnChannel(UpdateChannelRequestDto(name = name, handle = handle, description = description)) }

    override suspend fun imageSize(uri: String): Long? = withContext(Dispatchers.IO) {
        context.contentResolver.query(uri.toUri(), arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
    }

    override suspend fun setOwnChannelImage(kind: ChannelImageKind, uri: String): ChannelProfile {
        // Nom et type indicatifs : le serveur nomme lui-même le fichier et l'analyse avec ffprobe.
        val body = ContentUriRequestBody(
            resolver = context.contentResolver,
            uri = uri.toUri(),
            length = imageSize(uri) ?: -1L,
            mediaType = IMAGE_TYPE,
            onProgress = {},
        )
        val part = MultipartBody.Part.createFormData("image", "image.jpg", body)
        return announce {
            when (kind) {
                ChannelImageKind.Photo -> api.setPhoto(part)
                ChannelImageKind.Banner -> api.setBanner(part)
            }
        }
    }

    override suspend fun removeOwnChannelImage(kind: ChannelImageKind): ChannelProfile = announce {
        when (kind) {
            ChannelImageKind.Photo -> api.removePhoto()
            ChannelImageKind.Banner -> api.removeBanner()
        }
    }

    // Chaîne modifiée : les écrans qui l'affichent (Vous, page de chaîne) se rechargent.
    private suspend fun announce(call: suspend () -> ChannelProfileDto): ChannelProfile {
        val profile = apiCall(json) { call() }.toDomain()
        updated.emit(profile)
        return profile
    }

    override suspend fun setFollowing(channelId: String, follow: Boolean): FollowStatus {
        val status = apiCall(json) {
            if (follow) api.follow(channelId) else api.unfollow(channelId)
        }.toDomain()
        changes.emit(FollowChange(channelId, status))
        return status
    }

    override suspend fun setNotificationLevel(
        channelId: String,
        level: NotificationLevel,
        current: FollowStatus,
    ): FollowStatus {
        val saved = apiCall(json) { api.setNotificationLevel(channelId, NotificationLevelDto(level.apiValue)) }
        val status = current.copy(notificationLevel = notificationLevelOf(saved.notificationLevel))
        changes.emit(FollowChange(channelId, status))
        return status
    }
}

private val IMAGE_TYPE = "image/jpeg".toMediaType()
