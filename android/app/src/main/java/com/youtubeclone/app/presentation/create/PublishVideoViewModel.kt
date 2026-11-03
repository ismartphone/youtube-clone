package com.youtubeclone.app.presentation.create

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.Category
import com.youtubeclone.app.domain.model.UploadProgress
import com.youtubeclone.app.domain.model.VideoFormat
import com.youtubeclone.app.domain.model.VideoUpload
import com.youtubeclone.app.domain.model.VideoVisibility
import com.youtubeclone.app.domain.usecase.GetCategoriesUseCase
import com.youtubeclone.app.domain.usecase.UploadVideoUseCase
import com.youtubeclone.app.presentation.common.UiError
import com.youtubeclone.app.presentation.common.toUiError
import com.youtubeclone.app.presentation.navigation.PublishVideoRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Bornes de la route d'envoi pour le titre et la description.
const val TITLE_MAX_LENGTH = 100
const val DESCRIPTION_MAX_LENGTH = 1000

data class PublishVideoUiState(
    val uri: String,
    val format: VideoFormat,
    val title: String = "",
    val description: String = "",
    val categories: List<Category> = emptyList(),
    val categoryId: String? = null,
    /** Réglages de publication (ADR-016), modifiables ensuite depuis l'administration seulement. */
    val visibility: VideoVisibility = VideoVisibility.PUBLIC,
    val commentsEnabled: Boolean = true,
    /** La liste des catégories n'a pas pu être lue : l'écran propose de réessayer. */
    val categoriesFailed: Boolean = false,
    val isUploading: Boolean = false,
    /** Part envoyée, de 0 à 1 ; null tant qu'elle n'est pas connue (taille du fichier inconnue). */
    val progress: Float? = null,
    /** Dernier échec, ou annulation : affiché sous le formulaire. */
    val message: UiError? = null,
    /** La vidéo est publiée : l'écran se ferme, la confirmation s'affiche sur l'écran précédent. */
    val isPublished: Boolean = false,
) {
    /** Titre et catégorie sont obligatoires. */
    val canPublish: Boolean get() = title.isNotBlank() && categoryId != null && !isUploading
}

/** Écran de publication d'une vidéo ou d'un Short choisi dans la galerie (ADR-015). */
@HiltViewModel
class PublishVideoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getCategories: GetCategoriesUseCase,
    private val uploadVideo: UploadVideoUseCase,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<PublishVideoRoute>()

    private val _uiState = MutableStateFlow(
        PublishVideoUiState(uri = route.uri, format = VideoFormat.valueOf(route.format)),
    )
    val uiState: StateFlow<PublishVideoUiState> = _uiState.asStateFlow()

    private var uploadJob: Job? = null

    init {
        loadCategories()
    }

    /** Liste des catégories, obligatoire pour publier ; rechargée à la demande après un échec. */
    fun loadCategories() {
        _uiState.update { it.copy(categoriesFailed = false) }
        viewModelScope.launch {
            try {
                val categories = getCategories()
                _uiState.update { it.copy(categories = categories) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(categoriesFailed = true) }
            }
        }
    }

    fun onTitleChange(value: String) {
        _uiState.update { it.copy(title = value.take(TITLE_MAX_LENGTH)) }
    }

    fun onDescriptionChange(value: String) {
        _uiState.update { it.copy(description = value.take(DESCRIPTION_MAX_LENGTH)) }
    }

    fun onVisibilityChange(visibility: VideoVisibility) {
        _uiState.update { it.copy(visibility = visibility) }
    }

    fun onCommentsEnabledChange(enabled: Boolean) {
        _uiState.update { it.copy(commentsEnabled = enabled) }
    }

    fun onCategorySelected(categoryId: String) {
        _uiState.update { it.copy(categoryId = categoryId) }
    }

    fun publish() {
        val state = _uiState.value
        if (!state.canPublish) return
        _uiState.update { it.copy(isUploading = true, progress = 0f, message = null) }
        val categoryId = state.categoryId ?: return
        val upload = VideoUpload(
            uri = state.uri,
            format = state.format,
            title = state.title.trim(),
            description = state.description.trim().ifEmpty { null },
            categoryId = categoryId,
            visibility = state.visibility,
            commentsEnabled = state.commentsEnabled,
        )
        uploadJob = viewModelScope.launch {
            try {
                uploadVideo(upload).collect { progress ->
                    when (progress) {
                        is UploadProgress.Sending -> _uiState.update {
                            it.copy(progress = progress.fraction())
                        }
                        is UploadProgress.Published -> _uiState.update {
                            it.copy(isUploading = false, isPublished = true)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Le message du serveur (fichier refusé, trop gros...) est affiché tel quel.
                _uiState.update { it.copy(isUploading = false, progress = null, message = e.toUiError()) }
            }
        }
    }

    /** Arrête l'envoi en cours : rien n'est publié, le formulaire reste rempli. */
    fun cancel() {
        uploadJob?.cancel()
        uploadJob = null
        _uiState.update {
            it.copy(isUploading = false, progress = null, message = UiError(R.string.publish_canceled))
        }
    }

    private fun UploadProgress.Sending.fraction(): Float? =
        if (totalBytes > 0) (sentBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null
}
