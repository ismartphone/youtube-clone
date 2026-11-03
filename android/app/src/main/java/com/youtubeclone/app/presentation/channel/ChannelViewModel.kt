package com.youtubeclone.app.presentation.channel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.ChannelPage
import com.youtubeclone.app.domain.usecase.GetChannelPageUseCase
import com.youtubeclone.app.domain.usecase.ObserveFollowChangesUseCase
import com.youtubeclone.app.domain.usecase.ObservePublicationsUseCase
import com.youtubeclone.app.domain.usecase.ObserveSessionUseCase
import com.youtubeclone.app.domain.usecase.SetFollowingUseCase
import com.youtubeclone.app.presentation.common.UiError
import com.youtubeclone.app.presentation.common.toUiError
import com.youtubeclone.app.presentation.navigation.ChannelRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ChannelUiState {
    data object Loading : ChannelUiState
    data class Error(val error: UiError) : ChannelUiState
    data class Success(val page: ChannelPage) : ChannelUiState
}

@HiltViewModel
class ChannelViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getChannelPage: GetChannelPageUseCase,
    observeSession: ObserveSessionUseCase,
    setFollowing: SetFollowingUseCase,
    observeFollowChanges: ObserveFollowChangesUseCase,
    observePublications: ObservePublicationsUseCase,
) : ViewModel() {

    private val channelId = savedStateHandle.toRoute<ChannelRoute>().channelId

    private val _uiState = MutableStateFlow<ChannelUiState>(ChannelUiState.Loading)
    val uiState: StateFlow<ChannelUiState> = _uiState.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    val follow = FollowController(viewModelScope, setFollowing, observeFollowChanges)

    private var loadJob: Job? = null

    init {
        // Recharge à chaque connexion ou déconnexion : « Abonné » dépend de l'utilisateur.
        viewModelScope.launch {
            observeSession().collect { loggedIn ->
                _isLoggedIn.value = loggedIn
                load()
            }
        }
        // Contenu publié sur cette chaîne depuis l'application : la page le montre aussitôt.
        viewModelScope.launch {
            observePublications().collect { if (it.channelId == channelId) load() }
        }
    }

    fun load() {
        loadJob?.cancel()
        // Un rechargement garde la page affichée au lieu de repasser par le chargement.
        if (_uiState.value !is ChannelUiState.Success) _uiState.value = ChannelUiState.Loading
        loadJob = viewModelScope.launch {
            _uiState.value = try {
                val page = getChannelPage(channelId)
                follow.show(page.channel.id, page.followStatus, canFollow = !page.isOwner)
                ChannelUiState.Success(page)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ChannelUiState.Error(e.toUiError(notFoundRes = R.string.error_channel_not_found))
            }
        }
    }
}
