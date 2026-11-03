package com.youtubeclone.app.presentation.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeclone.app.domain.model.POST_TEXT_MAX_LENGTH
import com.youtubeclone.app.domain.model.PostImageProblem
import com.youtubeclone.app.domain.usecase.CheckPostImageUseCase
import com.youtubeclone.app.domain.usecase.CreatePostUseCase
import com.youtubeclone.app.presentation.common.UiError
import com.youtubeclone.app.presentation.common.toUiError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ComposePostUiState(
    val text: String = "",
    /** Image choisie dans la galerie, null sans image. */
    val imageUri: String? = null,
    /** Image refusée avant l'envoi (trop lourde, illisible). */
    val imageProblem: PostImageProblem? = null,
    val isSending: Boolean = false,
    val error: UiError? = null,
    /** Le post est publié : l'écran se ferme, la confirmation s'affiche sur l'écran précédent. */
    val isPublished: Boolean = false,
) {
    val canPublish: Boolean get() = text.isNotBlank() && !isSending
}

/** Rédaction d'un post (ADR-015) : texte obligatoire, image facultative. */
@HiltViewModel
class ComposePostViewModel @Inject constructor(
    private val checkPostImage: CheckPostImageUseCase,
    private val createPost: CreatePostUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ComposePostUiState())
    val uiState: StateFlow<ComposePostUiState> = _uiState.asStateFlow()

    fun onTextChange(value: String) {
        _uiState.update { it.copy(text = value.take(POST_TEXT_MAX_LENGTH)) }
    }

    /** Image choisie ([uri] null : sélecteur fermé sans choix). Une image trop lourde est refusée ici. */
    fun onImagePicked(uri: String?) {
        if (uri == null) return
        viewModelScope.launch {
            val problem = checkPostImage(uri)
            _uiState.update {
                it.copy(imageUri = if (problem == null) uri else it.imageUri, imageProblem = problem, error = null)
            }
        }
    }

    fun removeImage() {
        _uiState.update { it.copy(imageUri = null, imageProblem = null) }
    }

    fun publish() {
        val state = _uiState.value
        if (!state.canPublish) return
        _uiState.update { it.copy(isSending = true, error = null) }
        viewModelScope.launch {
            try {
                createPost(state.text, state.imageUri)
                _uiState.update { it.copy(isSending = false, isPublished = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Le message du serveur (image refusée, trop lourde...) est affiché tel quel.
                _uiState.update { it.copy(isSending = false, error = e.toUiError()) }
            }
        }
    }
}
