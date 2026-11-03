package com.youtubeclone.app.presentation.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeclone.app.domain.model.Channel
import com.youtubeclone.app.domain.usecase.GetOwnChannelUseCase
import com.youtubeclone.app.domain.usecase.ObserveSessionUseCase
import com.youtubeclone.app.presentation.common.UiError
import com.youtubeclone.app.presentation.common.toUiError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface CreateUiState {
    data object Loading : CreateUiState
    data object LoggedOut : CreateUiState
    /** Connecté sans chaîne : la feuille invite à en créer une (ADR-015). */
    data object NoChannel : CreateUiState
    data class Error(val error: UiError) : CreateUiState
    data class Ready(val channel: Channel) : CreateUiState
}

/** Feuille de création du bouton + : seul le propriétaire d'une chaîne peut publier (ADR-015). */
@HiltViewModel
class CreateViewModel @Inject constructor(
    private val observeSession: ObserveSessionUseCase,
    private val getOwnChannel: GetOwnChannelUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CreateUiState>(CreateUiState.Loading)
    val uiState: StateFlow<CreateUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /** Relu à chaque ouverture : la session ou la chaîne ont pu changer depuis. */
    fun load() {
        loadJob?.cancel()
        _uiState.value = CreateUiState.Loading
        loadJob = viewModelScope.launch {
            _uiState.value = try {
                when {
                    !observeSession().first() -> CreateUiState.LoggedOut
                    else -> getOwnChannel()?.let(CreateUiState::Ready) ?: CreateUiState.NoChannel
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CreateUiState.Error(e.toUiError())
            }
        }
    }
}
