package com.youtubeclone.app.presentation.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.ChannelImageKind
import com.youtubeclone.app.domain.model.ChannelProfile
import com.youtubeclone.app.domain.model.DataError
import com.youtubeclone.app.domain.model.MAX_CHANNEL_IMAGE_BYTES
import com.youtubeclone.app.domain.model.isValidHandle
import com.youtubeclone.app.domain.usecase.ChannelImageProblem
import com.youtubeclone.app.domain.usecase.GetChannelPageUseCase
import com.youtubeclone.app.domain.usecase.GetOwnChannelUseCase
import com.youtubeclone.app.domain.usecase.RemoveOwnChannelImageUseCase
import com.youtubeclone.app.domain.usecase.SetOwnChannelImageUseCase
import com.youtubeclone.app.domain.usecase.UpdateOwnChannelUseCase
import com.youtubeclone.app.presentation.common.UiError
import com.youtubeclone.app.presentation.common.toUiError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Formulaire de personnalisation : la chaîne enregistrée et les champs en cours de saisie. */
data class CustomizeChannelForm(
    val profile: ChannelProfile,
    val name: String,
    val handle: String,
    val description: String,
    val isSaving: Boolean = false,
    /** Image en cours d'envoi ou de retrait ; null sinon. */
    val imageBusy: ChannelImageKind? = null,
    /** Handle refusé par le serveur (déjà pris) : affiché sous le champ. */
    val handleError: UiError? = null,
    /** Autre échec de l'enregistrement : affiché sous le formulaire. */
    val error: UiError? = null,
) {
    val isHandleMalformed: Boolean get() = handle.isNotEmpty() && !isValidHandle(handle)

    private val hasChanges: Boolean
        get() = name.trim() != profile.channel.name ||
            handle != profile.channel.handle ||
            description.trim() != profile.description.orEmpty()

    val canSave: Boolean get() = hasChanges && name.isNotBlank() && isValidHandle(handle) && !isSaving
}

sealed interface CustomizeChannelUiState {
    data object Loading : CustomizeChannelUiState
    data class Error(val error: UiError) : CustomizeChannelUiState
    data class Ready(val form: CustomizeChannelForm) : CustomizeChannelUiState
}

/**
 * Personnalisation de sa chaîne (ADR-018). Les images sont envoyées dès qu'elles sont choisies ;
 * le nom, le handle et la description, au bouton Enregistrer.
 */
@HiltViewModel
class CustomizeChannelViewModel @Inject constructor(
    private val getOwnChannel: GetOwnChannelUseCase,
    private val getChannelPage: GetChannelPageUseCase,
    private val updateOwnChannel: UpdateOwnChannelUseCase,
    private val setImage: SetOwnChannelImageUseCase,
    private val removeImage: RemoveOwnChannelImageUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CustomizeChannelUiState>(CustomizeChannelUiState.Loading)
    val uiState: StateFlow<CustomizeChannelUiState> = _uiState.asStateFlow()

    // Échecs d'envoi d'image et confirmation d'enregistrement, affichés en message bref.
    private val _messages = MutableSharedFlow<UiError>(extraBufferCapacity = 1)
    val messages: SharedFlow<UiError> = _messages.asSharedFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = CustomizeChannelUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                val channel = getOwnChannel() ?: throw DataError.NotFound
                val page = getChannelPage(channel.id)
                val profile = ChannelProfile(page.channel, page.bannerUrl, page.description)
                CustomizeChannelUiState.Ready(formOf(profile))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CustomizeChannelUiState.Error(e.toUiError(notFoundRes = R.string.error_channel_not_found))
            }
        }
    }

    fun onNameChange(value: String) = updateForm { it.copy(name = value.take(CHANNEL_NAME_MAX_LENGTH)) }

    /** Le « @ » éventuel est retiré, et le handle est mis en minuscules comme sur le serveur. */
    fun onHandleChange(value: String) =
        updateForm { it.copy(handle = value.trim().removePrefix("@").lowercase(), handleError = null) }

    fun onDescriptionChange(value: String) =
        updateForm { it.copy(description = value.take(CHANNEL_DESCRIPTION_MAX_LENGTH)) }

    fun save() {
        val form = currentForm() ?: return
        if (!form.canSave) return
        updateForm { it.copy(isSaving = true, handleError = null, error = null) }
        viewModelScope.launch {
            try {
                val profile = updateOwnChannel(form.name, form.handle, form.description)
                _uiState.value = CustomizeChannelUiState.Ready(formOf(profile))
                _messages.tryEmit(UiError(R.string.customize_channel_saved))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 409 : handle déjà pris (le message du serveur le dit), à corriger dans son champ.
                val conflict = e is DataError.Server && e.code == HTTP_CONFLICT
                updateForm {
                    it.copy(
                        isSaving = false,
                        handleError = if (conflict) e.toUiError() else null,
                        error = if (conflict) null else e.toUiError(),
                    )
                }
            }
        }
    }

    /** Image choisie dans la galerie ([uri] null : sélecteur fermé sans choix). */
    fun onImagePicked(kind: ChannelImageKind, uri: String?) {
        if (uri == null || currentForm()?.imageBusy != null) return
        runImage(kind) {
            when (setImage.check(uri)) {
                ChannelImageProblem.Unreadable -> throw ImageRefused(UiError(R.string.customize_channel_image_unreadable))
                // Le code sert d'argument au message : la limite en Mo.
                ChannelImageProblem.TooLarge -> throw ImageRefused(
                    UiError(R.string.customize_channel_image_too_large, code = (MAX_CHANNEL_IMAGE_BYTES / 1024 / 1024).toInt()),
                )
                null -> setImage(kind, uri)
            }
        }
    }

    fun onRemoveImage(kind: ChannelImageKind) {
        if (currentForm()?.imageBusy != null) return
        runImage(kind) { removeImage(kind) }
    }

    // Une image change sans toucher aux champs en cours de saisie.
    private fun runImage(kind: ChannelImageKind, action: suspend () -> ChannelProfile) {
        updateForm { it.copy(imageBusy = kind) }
        viewModelScope.launch {
            try {
                val profile = action()
                updateForm {
                    it.copy(
                        profile = it.profile.copy(channel = profile.channel, bannerUrl = profile.bannerUrl),
                        imageBusy = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImageRefused) {
                updateForm { it.copy(imageBusy = null) }
                _messages.tryEmit(e.error)
            } catch (e: Exception) {
                updateForm { it.copy(imageBusy = null) }
                _messages.tryEmit(e.toUiError())
            }
        }
    }

    private fun currentForm() = (_uiState.value as? CustomizeChannelUiState.Ready)?.form

    private fun updateForm(transform: (CustomizeChannelForm) -> CustomizeChannelForm) {
        _uiState.update { state ->
            if (state is CustomizeChannelUiState.Ready) state.copy(form = transform(state.form)) else state
        }
    }

    private fun formOf(profile: ChannelProfile) = CustomizeChannelForm(
        profile = profile,
        name = profile.channel.name,
        handle = profile.channel.handle,
        description = profile.description.orEmpty(),
    )

    private class ImageRefused(val error: UiError) : Exception()

    private companion object {
        const val HTTP_CONFLICT = 409
    }
}
