package com.youtubeclone.app.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeclone.app.domain.model.ChannelSearchResult
import com.youtubeclone.app.domain.model.SearchPage
import com.youtubeclone.app.domain.model.Video
import com.youtubeclone.app.domain.usecase.SearchUseCase
import com.youtubeclone.app.presentation.common.UiError
import com.youtubeclone.app.presentation.common.toUiError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    /** Champ vide : rien n'est cherché. */
    data object Initial : SearchUiState
    data object Loading : SearchUiState
    /** Aucune vidéo ni chaîne pour [query]. */
    data class Empty(val query: String) : SearchUiState
    data class Error(val error: UiError) : SearchUiState
    data class Success(
        val videos: List<Video>,
        val channels: List<ChannelSearchResult>,
        val canLoadMore: Boolean,
        val isLoadingMore: Boolean = false,
    ) : SearchUiState
}

// Délai après la dernière frappe avant d'interroger l'API.
private const val TYPING_DEBOUNCE_MS = 300L

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val search: SearchUseCase,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Initial)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    // Échecs du chargement des pages suivantes : les résultats déjà affichés restent en place.
    private val _errors = MutableSharedFlow<UiError>(extraBufferCapacity = 1)
    val errors: SharedFlow<UiError> = _errors.asSharedFlow()

    private var searchJob: Job? = null
    private var lastPage = 0

    /** Chaque frappe relance la recherche après un court délai ; la précédente est abandonnée. */
    fun onQueryChange(text: String) {
        _query.value = text.take(SearchUseCase.MAX_LENGTH)
        launchSearch(debounce = true)
    }

    /** Touche « Rechercher » du clavier, ou nouvel essai après une erreur : sans attendre. */
    fun searchNow() = launchSearch(debounce = false)

    fun loadMore() {
        val current = _uiState.value as? SearchUiState.Success ?: return
        if (!current.canLoadMore || current.isLoadingMore) return
        val term = _query.value.trim()
        _uiState.value = current.copy(isLoadingMore = true)
        searchJob = viewModelScope.launch {
            try {
                val page = search(term, page = lastPage + 1)
                lastPage = page.videos.page
                _uiState.update {
                    SearchUiState.Success(
                        videos = (current.videos + page.videos.items).distinctBy(Video::id),
                        channels = (current.channels + page.channels).distinctBy { it.channel.id },
                        canLoadMore = page.hasMore(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { (it as? SearchUiState.Success)?.copy(isLoadingMore = false) ?: it }
                _errors.tryEmit(e.toUiError())
            }
        }
    }

    private fun launchSearch(debounce: Boolean) {
        searchJob?.cancel()
        val term = _query.value.trim()
        if (term.isEmpty()) {
            _uiState.value = SearchUiState.Initial
            return
        }
        searchJob = viewModelScope.launch {
            if (debounce) delay(TYPING_DEBOUNCE_MS)
            _uiState.value = SearchUiState.Loading
            _uiState.value = try {
                val page = search(term)
                lastPage = 1
                if (page.videos.items.isEmpty() && page.channels.isEmpty()) {
                    SearchUiState.Empty(term)
                } else {
                    SearchUiState.Success(page.videos.items, page.channels, canLoadMore = page.hasMore())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SearchUiState.Error(e.toUiError())
            }
        }
    }

    // Les deux sections partagent la pagination : il reste une page tant que l'une n'est pas finie.
    private fun SearchPage.hasMore(): Boolean {
        val loaded = videos.page * videos.pageSize
        return loaded < videos.total || loaded < channelTotal
    }
}
