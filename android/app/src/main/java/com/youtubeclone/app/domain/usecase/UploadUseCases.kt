package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.model.PickedVideoProblem
import com.youtubeclone.app.domain.model.UploadProgress
import com.youtubeclone.app.domain.model.VideoFormat
import com.youtubeclone.app.domain.model.VideoUpload
import com.youtubeclone.app.domain.model.problemFor
import com.youtubeclone.app.domain.repository.UploadRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Vérifie qu'une vidéo choisie convient au format demandé ; null si elle convient. */
class CheckPickedVideoUseCase @Inject constructor(
    private val repository: UploadRepository,
) {
    suspend operator fun invoke(uri: String, format: VideoFormat): PickedVideoProblem? {
        val video = repository.inspect(uri) ?: return PickedVideoProblem.Unreadable
        return video.problemFor(format)
    }
}

class UploadVideoUseCase @Inject constructor(
    private val repository: UploadRepository,
) {
    operator fun invoke(upload: VideoUpload): Flow<UploadProgress> = repository.upload(upload)
}
