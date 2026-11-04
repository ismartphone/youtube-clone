package com.youtubeclone.app.data.repository

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.youtubeclone.app.data.local.DownloadDao
import com.youtubeclone.app.data.local.DownloadedVideoEntity
import com.youtubeclone.app.data.remote.DownloadApi
import com.youtubeclone.app.data.remote.dto.CheckDownloadsRequestDto
import com.youtubeclone.app.data.remote.resolveMediaUrl
import com.youtubeclone.app.domain.model.DataError
import com.youtubeclone.app.domain.model.DownloadProblem
import com.youtubeclone.app.domain.model.DownloadState
import com.youtubeclone.app.domain.model.DownloadedVideo
import com.youtubeclone.app.domain.model.MAX_DOWNLOADS
import com.youtubeclone.app.domain.model.OFFLINE_REVALIDATION_DAYS
import com.youtubeclone.app.domain.model.Video
import com.youtubeclone.app.domain.repository.DownloadsRepository
import com.youtubeclone.app.playback.download.AppDownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.HttpException

/**
 * Téléchargements hors ligne (ADR-019, partie 2). Le serveur autorise chaque téléchargement ; le
 * DownloadManager de Media3 écrit la vidéo dans le cache privé ; Room garde ce que l'écran
 * « Téléchargements » affiche sans réseau. Miniature et sous-titres sont enregistrés à côté, dans
 * le stockage privé, jamais dans la galerie.
 */
@OptIn(UnstableApi::class)
class DownloadsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: DownloadApi,
    private val dao: DownloadDao,
    private val manager: DownloadManager,
    private val client: OkHttpClient,
) : DownloadsRepository {

    // État des téléchargements du DownloadManager, relu chaque seconde : la progression avance.
    private val states: Flow<Map<String, Download>> = flow {
        resumePending()
        while (true) {
            emit(withContext(Dispatchers.IO) { readStates() })
            delay(PROGRESS_REFRESH_MS)
        }
    }

    // Le DownloadManager démarre en pause, et seul le service le relance : après un redémarrage
    // de l'application, un téléchargement resté en file ne reprend que si le service repart.
    // Appelé quand un écran affiche les téléchargements, donc au premier plan, comme l'exige Android.
    private suspend fun resumePending() {
        val pending = withContext(Dispatchers.IO) { readStates() }.values.any { it.state != Download.STATE_COMPLETED }
        if (pending) runCatching { DownloadService.start(context, AppDownloadService::class.java) }
    }

    override val downloads: Flow<List<DownloadedVideo>> =
        combine(dao.observeAll(), states) { rows, downloads -> rows.map { it.toDomain(downloads[it.videoId]) } }

    override suspend fun download(video: Video) {
        if (dao.get(video.id) != null) return
        if (dao.count() >= MAX_DOWNLOADS) throw DownloadProblem.TooMany
        val grant = try {
            api.authorize(video.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            throw when (e.code()) {
                403 -> DataError.PremiumRequired
                404 -> DataError.NotFound
                else -> DataError.Server(e.code())
            }
        } catch (_: IOException) {
            throw DataError.Network
        }
        // Espace libre : la taille annoncée, plus une marge pour la miniature et les sous-titres.
        val size = grant.sizeBytes ?: 0L
        if (context.filesDir.usableSpace < size + SPACE_MARGIN_BYTES) throw DownloadProblem.NotEnoughSpace

        val now = System.currentTimeMillis()
        val thumbnail = saveFile(video.thumbnailUrl, THUMBNAILS_FOLDER, "${video.id}.jpg")
        val subtitles = grant.subtitleUrl?.let { saveFile(resolveMediaUrl(it), SUBTITLES_FOLDER, "${video.id}.vtt") }
        dao.insert(
            DownloadedVideoEntity(
                videoId = video.id,
                title = video.title,
                channelName = video.channel.name,
                durationSeconds = video.durationSeconds,
                thumbnailPath = thumbnail,
                subtitlePath = subtitles,
                sizeBytes = grant.sizeBytes,
                expiresAt = Instant.parse(grant.expiresAt).toEpochMilli(),
                validatedAt = now,
                createdAt = now,
            ),
        )
        val request = DownloadRequest.Builder(video.id, resolveMediaUrl(grant.fileUrl).toUri()).build()
        DownloadService.sendAddDownload(context, AppDownloadService::class.java, request, false)
    }

    override suspend fun remove(videoId: String) {
        DownloadService.sendRemoveDownload(context, AppDownloadService::class.java, videoId, false)
        withContext(Dispatchers.IO) {
            File(context.filesDir, "$THUMBNAILS_FOLDER/$videoId.jpg").delete()
            File(context.filesDir, "$SUBTITLES_FOLDER/$videoId.vtt").delete()
        }
        dao.delete(videoId)
    }

    override suspend fun removeAll() {
        dao.getAll().forEach { remove(it.videoId) }
        DownloadService.sendRemoveAllDownloads(context, AppDownloadService::class.java, false)
    }

    override suspend fun revalidate() {
        val rows = dao.getAll()
        if (rows.isEmpty()) return
        val check = try {
            api.check(CheckDownloadsRequestDto(rows.map { it.videoId }))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Sans réseau, ou serveur injoignable : rien ne change, le délai de 30 jours court.
            return
        }
        val expiresAt = check.expiresAt?.let { Instant.parse(it).toEpochMilli() }
        if (expiresAt == null) {
            removeAll()
            return
        }
        rows.filterNot { it.videoId in check.accessible }.forEach { remove(it.videoId) }
        if (check.accessible.isNotEmpty()) dao.revalidate(check.accessible, expiresAt, System.currentTimeMillis())
    }

    override suspend fun get(videoId: String): DownloadedVideo? =
        dao.get(videoId)?.toDomain(withContext(Dispatchers.IO) { readStates()[videoId] })

    private fun readStates(): Map<String, Download> {
        val result = mutableMapOf<String, Download>()
        manager.downloadIndex.getDownloads().use { cursor ->
            while (cursor.moveToNext()) result[cursor.download.request.id] = cursor.download
        }
        return result
    }

    // Un échec laisse simplement la miniature ou les sous-titres absents : la vidéo reste lisible.
    private suspend fun saveFile(url: String, folder: String, name: String): String? = withContext(Dispatchers.IO) {
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val body = response.body
                if (!response.isSuccessful || body == null) return@use null
                val file = File(context.filesDir, "$folder/$name").apply { parentFile?.mkdirs() }
                file.outputStream().use { body.byteStream().copyTo(it) }
                file.absolutePath
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun DownloadedVideoEntity.toDomain(download: Download?): DownloadedVideo {
        val state = when (download?.state) {
            Download.STATE_COMPLETED -> DownloadState.Completed
            Download.STATE_DOWNLOADING -> DownloadState.Downloading
            Download.STATE_FAILED -> DownloadState.Failed
            else -> DownloadState.Queued
        }
        val now = System.currentTimeMillis()
        val stale = now - validatedAt > TimeUnit.DAYS.toMillis(OFFLINE_REVALIDATION_DAYS)
        return DownloadedVideo(
            videoId = videoId,
            title = title,
            channelName = channelName,
            durationSeconds = durationSeconds,
            thumbnailPath = thumbnailPath,
            subtitlePath = subtitlePath,
            sizeBytes = download?.bytesDownloaded?.takeIf { state == DownloadState.Completed } ?: sizeBytes ?: 0L,
            state = state,
            progress = (download?.percentDownloaded ?: 0f).coerceIn(0f, 100f) / 100f,
            playable = state == DownloadState.Completed && now < expiresAt && !stale,
            needsRevalidation = stale,
        )
    }

    private companion object {
        const val PROGRESS_REFRESH_MS = 1_000L
        const val SPACE_MARGIN_BYTES = 50L * 1024 * 1024
        const val THUMBNAILS_FOLDER = "download-thumbnails"
        const val SUBTITLES_FOLDER = "download-subtitles"
    }
}
