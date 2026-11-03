package com.youtubeclone.app.data.remote

import android.content.ContentResolver
import android.net.Uri
import com.youtubeclone.app.data.remote.dto.VideoDetailDto
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface UploadApi {
    // Route protégée, réservée au propriétaire d'une chaîne (ADR-015). Un champ null est omis.
    @Multipart
    @POST("me/videos")
    suspend fun uploadVideo(
        @Part file: MultipartBody.Part,
        @Part("title") title: RequestBody,
        @Part("description") description: RequestBody?,
        @Part("categoryId") categoryId: RequestBody,
        @Part("format") format: RequestBody,
    ): VideoDetailDto
}

/**
 * Corps d'envoi lu directement depuis la galerie, sans copie : [onProgress] reçoit le nombre
 * d'octets envoyés. Relisible : si la requête est rejouée (token rafraîchi), l'envoi repart
 * du début.
 */
internal class ContentUriRequestBody(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val length: Long,
    private val mediaType: MediaType?,
    private val onProgress: (sentBytes: Long) -> Unit,
) : RequestBody() {

    override fun contentType() = mediaType

    override fun contentLength() = length

    override fun writeTo(sink: BufferedSink) {
        val input = resolver.openInputStream(uri) ?: throw java.io.IOException("Fichier illisible : $uri")
        input.use {
            val buffer = ByteArray(BUFFER_SIZE)
            var sent = 0L
            onProgress(0)
            while (true) {
                val read = it.read(buffer)
                if (read == -1) break
                sink.write(buffer, 0, read)
                sent += read
                onProgress(sent)
            }
        }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
