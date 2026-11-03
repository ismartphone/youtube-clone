package com.youtubeclone.app.presentation.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeclone.app.domain.model.DataError
import com.youtubeclone.app.domain.model.isValidHandle
import com.youtubeclone.app.domain.model.suggestHandle
import com.youtubeclone.app.domain.usecase.CreateChannelUseCase
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

// Bornes du serveur pour le nom et la description.
const val CHANNEL_NAME_MAX_LENGTH = 50
const val CHANNEL_DESCRIPTION_MAX_LENGTH = 1000

data class CreateChannelUiState(
    val name: String = "",
    val handle: String = "",
    /** Le créateur a modifié le handle : il ne suit plus le nom. */
    val handleEdited: Boolean = false,
    val description: String = "",
    val isSaving: Boolean = false,
    /** Handle refusé par le serveur (déjà pris) : affiché sous le champ. */
    val handleError: UiError? = null,
    /** Autre échec : affiché sous le formulaire. */
    val error: UiError? = null,
    /** Chaîne créée : l'écran s'ouvre sur elle. */
    val createdChannelId: String? = null,
) {
    /** Handle saisi mais au mauvais format : l'écran rappelle la règle. */
    val isHandleMalformed: Boolean get() = handle.isNotEmpty() && !isValidHandle(handle)

    val canCreate: Boolean get() = name.isNotBlank() && isValidHandle(handle) && !isSaving
}

@HiltViewModel
class CreateChannelViewModel @Inject constructor(
    private val createChannel: CreateChannelUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateChannelUiState())
    val uiState: StateFlow<CreateChannelUiState> = _uiState.asStateFlow()

    /** Tant que le handle n'a pas été modifié à la main, il est proposé à partir du nom. */
    fun onNameChange(value: String) {
        _uiState.update {
            val name = value.take(CHANNEL_NAME_MAX_LENGTH)
            it.copy(
                name = name,
                handle = if (it.handleEdited) it.handle else suggestHandle(name),
                handleError = if (it.handleEdited) it.handleError else null,
            )
        }
    }

    /** Le « @ » éventuel est retiré, et le handle est mis en minuscules comme sur le serveur. */
    fun onHandleChange(value: String) {
        val handle = value.trim().removePrefix("@").lowercase()
        _uiState.update { it.copy(handle = handle, handleEdited = true, handleError = null) }
    }

    fun onDescriptionChange(value: String) {
        _uiState.update { it.copy(description = value.take(CHANNEL_DESCRIPTION_MAX_LENGTH)) }
    }

    fun create() {
        val state = _uiState.value
        if (!state.canCreate) return
        _uiState.update { it.copy(isSaving = true, handleError = null, error = null) }
        viewModelScope.launch {
            try {
                val channel = createChannel(state.name, state.handle, state.description)
                _uiState.update { it.copy(isSaving = false, createdChannelId = channel.id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 409 : handle déjà pris (le message du serveur le dit), à corriger dans son champ.
                val conflict = e is DataError.Server && e.code == HTTP_CONFLICT
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        handleError = if (conflict) e.toUiError() else null,
                        error = if (conflict) null else e.toUiError(),
                    )
                }
            }
        }
    }

    private companion object {
        const val HTTP_CONFLICT = 409
    }
}
