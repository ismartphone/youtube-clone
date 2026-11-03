package com.youtubeclone.app.data.repository

import android.content.Context
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.youtubeclone.app.data.remote.ContentUriRequestBody
import com.youtubeclone.app.data.remote.PostApi
import com.youtubeclone.app.data.remote.apiCall
import com.youtubeclone.app.data.remote.dto.toDomain
import com.youtubeclone.app.domain.model.Post
import com.youtubeclone.app.domain.model.PostPage
import com.youtubeclone.app.domain.model.Publication
import com.youtubeclone.app.domain.repository.PostRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class PostRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: PostApi,
    private val json: Json,
    private val publicationFeed: InMemoryPublicationFeed,
) : PostRepository {

    override suspend fun imageSize(uri: String): Long? = withContext(Dispatchers.IO) {
        context.contentResolver.query(uri.toUri(), arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
    }

    override suspend fun createPost(text: String, imageUri: String?): Post {
        // Nom et type indicatifs : le serveur nomme lui-même le fichier et l'analyse avec ffprobe.
        val image = imageUri?.let { uri ->
            val body = ContentUriRequestBody(
                resolver = context.contentResolver,
                uri = uri.toUri(),
                length = imageSize(uri) ?: -1L,
                mediaType = IMAGE_TYPE,
                onProgress = {},
            )
            MultipartBody.Part.createFormData("image", "image.jpg", body)
        }
        val post = apiCall(json) { api.createPost(text.toRequestBody(TEXT_TYPE), image) }.toDomain()
        publicationFeed.announce(Publication.PostPublished(post))
        return post
    }

    override suspend fun getChannelPosts(channelId: String, page: Int, pageSize: Int): PostPage =
        apiCall(json) { api.getChannelPosts(channelId, page, pageSize) }.toDomain()

    private companion object {
        val IMAGE_TYPE = "image/jpeg".toMediaType()
        val TEXT_TYPE = "text/plain".toMediaType()
    }
}
