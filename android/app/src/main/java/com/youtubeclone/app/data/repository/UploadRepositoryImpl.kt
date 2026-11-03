package com.youtubeclone.app.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.youtubeclone.app.data.remote.ContentUriRequestBody
import com.youtubeclone.app.data.remote.UploadApi
import com.youtubeclone.app.data.remote.apiCall
import com.youtubeclone.app.data.remote.dto.toDomain
import com.youtubeclone.app.domain.model.PickedVideo
import com.youtubeclone.app.domain.model.UploadProgress
import com.youtubeclone.app.domain.model.VideoUpload
import com.youtubeclone.app.domain.repository.UploadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class UploadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: UploadApi,
    private val json: Json,
) : UploadRepository {

    override suspend fun inspect(uri: String): PickedVideo? = withContext(Dispatchers.IO) {
        val contentUri = uri.toUri()
        val size = sizeOf(uri) ?: return@withContext null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, contentUri)
            fun read(key: Int) = retriever.extractMetadata(key)?.toLongOrNull()
            val duration = read(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val width = read(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt()
            val height = read(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt()
            if (duration == null || width == null || height == null) return@withContext null
            // Vidéo tournée d'un quart de tour : affichée avec largeur et hauteur inversées.
            val rotation = read(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) ?: 0
            val quarterTurn = rotation % 180 == 90L
            PickedVideo(
                uri = uri,
                durationMs = duration,
                width = if (quarterTurn) height else width,
                height = if (quarterTurn) width else height,
                sizeBytes = size,
            )
        } catch (_: RuntimeException) {
            null // fichier qui n'est pas une vidéo, ou que le lecteur de métadonnées refuse
        } finally {
            retriever.release()
        }
    }

    override fun upload(upload: VideoUpload): Flow<UploadProgress> = channelFlow {
        val total = sizeOf(upload.uri) ?: -1L
        val body = ContentUriRequestBody(
            resolver = context.contentResolver,
            uri = upload.uri.toUri(),
            length = total,
            mediaType = VIDEO_TYPE,
        ) { sent -> trySend(UploadProgress.Sending(sent, total)) }
        // Nom et type indicatifs : le serveur génère son propre nom et analyse le fichier avec ffprobe.
        val file = MultipartBody.Part.createFormData("file", "video.mp4", body)
        val detail = apiCall(json) {
            api.uploadVideo(
                file = file,
                title = upload.title.toTextPart(),
                description = upload.description?.toTextPart(),
                categoryId = upload.categoryId?.toTextPart(),
                format = upload.format.name.toTextPart(),
            )
        }
        send(UploadProgress.Published(detail.toDomain().video))
    }
        // L'écran n'a besoin que du dernier avancement : les étapes intermédiaires sont sautées.
        .conflate()
        .flowOn(Dispatchers.IO)

    // Taille annoncée par le fournisseur du fichier (galerie) ; null si inconnue.
    private fun sizeOf(uri: String): Long? =
        context.contentResolver.query(uri.toUri(), arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }

    private fun String.toTextPart() = toRequestBody(TEXT_TYPE)

    private companion object {
        val VIDEO_TYPE = "video/mp4".toMediaType()
        val TEXT_TYPE = "text/plain".toMediaType()
    }
}
