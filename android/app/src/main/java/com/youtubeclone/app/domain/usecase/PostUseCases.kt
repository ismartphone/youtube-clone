package com.youtubeclone.app.domain.usecase

import com.youtubeclone.app.domain.model.MAX_POST_IMAGE_BYTES
import com.youtubeclone.app.domain.model.Post
import com.youtubeclone.app.domain.model.PostImageProblem
import com.youtubeclone.app.domain.model.PostPage
import com.youtubeclone.app.domain.repository.PostRepository
import javax.inject.Inject

/** Vérifie qu'une image choisie peut accompagner un post ; null si elle convient. */
class CheckPostImageUseCase @Inject constructor(
    private val repository: PostRepository,
) {
    suspend operator fun invoke(uri: String): PostImageProblem? {
        val size = repository.imageSize(uri) ?: return PostImageProblem.Unreadable
        return if (size > MAX_POST_IMAGE_BYTES) PostImageProblem.TooLarge else null
    }
}

class CreatePostUseCase @Inject constructor(
    private val repository: PostRepository,
) {
    suspend operator fun invoke(text: String, imageUri: String?): Post =
        repository.createPost(text.trim(), imageUri)
}

class GetChannelPostsUseCase @Inject constructor(
    private val repository: PostRepository,
) {
    suspend operator fun invoke(channelId: String, page: Int = 1, pageSize: Int = PAGE_SIZE): PostPage =
        repository.getChannelPosts(channelId, page, pageSize)

    private companion object {
        const val PAGE_SIZE = 20
    }
}
