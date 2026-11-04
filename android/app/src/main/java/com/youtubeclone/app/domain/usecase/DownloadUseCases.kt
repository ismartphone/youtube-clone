package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.model.DownloadedVideo
import com.youtubeclone.app.domain.model.Video
import com.youtubeclone.app.domain.repository.DownloadsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

// Téléchargements hors ligne, réservés au Premium (ADR-019, partie 2).

class ObserveDownloadsUseCase @Inject constructor(private val repository: DownloadsRepository) {
    operator fun invoke(): Flow<List<DownloadedVideo>> = repository.downloads
}

class DownloadVideoUseCase @Inject constructor(private val repository: DownloadsRepository) {
    suspend operator fun invoke(video: Video) = repository.download(video)
}

class RemoveDownloadUseCase @Inject constructor(private val repository: DownloadsRepository) {
    suspend operator fun invoke(videoId: String) = repository.remove(videoId)
}

class GetDownloadUseCase @Inject constructor(private val repository: DownloadsRepository) {
    suspend operator fun invoke(videoId: String): DownloadedVideo? = repository.get(videoId)
}

class RevalidateDownloadsUseCase @Inject constructor(private val repository: DownloadsRepository) {
    suspend operator fun invoke() = repository.revalidate()
}
