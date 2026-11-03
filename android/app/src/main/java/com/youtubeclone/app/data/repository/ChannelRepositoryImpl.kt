package com.youtubeclone.app.data.repository

import com.youtubeclone.app.data.remote.ChannelApi
import com.youtubeclone.app.data.remote.apiCall
import com.youtubeclone.app.data.remote.dto.CreateChannelRequestDto
import com.youtubeclone.app.data.remote.dto.toDomain
import com.youtubeclone.app.domain.model.Channel
import com.youtubeclone.app.domain.model.ChannelPage
import com.youtubeclone.app.domain.model.FollowChange
import com.youtubeclone.app.domain.model.FollowStatus
import com.youtubeclone.app.domain.repository.ChannelRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json

class ChannelRepositoryImpl @Inject constructor(
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

    override suspend fun setFollowing(channelId: String, follow: Boolean): FollowStatus {
        val status = apiCall(json) {
            if (follow) api.follow(channelId) else api.unfollow(channelId)
        }.toDomain()
        changes.emit(FollowChange(channelId, status))
        return status
    }
}
