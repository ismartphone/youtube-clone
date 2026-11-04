package com.youtubeclone.app.presentation.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeclone.app.domain.analytics.AdPlacement
import com.youtubeclone.app.domain.analytics.Analytics
import com.youtubeclone.app.domain.analytics.AnalyticsEvent
import com.youtubeclone.app.domain.model.Category
import com.youtubeclone.app.domain.model.Post
import com.youtubeclone.app.domain.model.User
import com.youtubeclone.app.domain.model.Video
import com.youtubeclone.app.domain.model.VideoAd
import com.youtubeclone.app.domain.usecase.GetCategoriesUseCase
import com.youtubeclone.app.domain.usecase.GetCurrentUserUseCase
import com.youtubeclone.app.domain.usecase.GetHomeFeedUseCase
import com.youtubeclone.app.domain.usecase.GetLatestPostUseCase
import com.youtubeclone.app.domain.usecase.GetSponsoredAdUseCase
import com.youtubeclone.app.domain.usecase.ObserveInterestsUseCase
import com.youtubeclone.app.domain.usecase.ObservePremiumStatusUseCase
import com.youtubeclone.app.domain.usecase.ObservePublicationsUseCase
import com.youtubeclone.app.domain.usecase.ObserveSessionUseCase
import com.youtubeclone.app.presentation.ads.SponsoredAdPreview
import com.youtubeclone.app.presentation.common.UiError
import com.youtubeclone.app.presentation.common.toUiError
import com.youtubeclone.app.presentation.onboarding.FirstScreenReadiness
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Empty : HomeUiState
    data class Error(val error: UiError) : HomeUiState
    data class Success(val videos: List<Video>) : HomeUiState
}

/** Rangée de filtres : « Tout » (catégorie null) puis les catégories du catalogue. */
data class HomeFilters(
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val getHomeFeed: GetHomeFeedUseCase,
    private val getCategories: GetCategoriesUseCase,
    private val getCurrentUser: GetCurrentUserUseCase,
    observeInterests: ObserveInterestsUseCase,
    observeSession: ObserveSessionUseCase,
    observePublications: ObservePublicationsUseCase,
    private val firstScreenReadiness: FirstScreenReadiness,
    private val getSponsoredAd: GetSponsoredAdUseCase,
    observePremium: ObservePremiumStatusUseCase,
    private val analytics: Analytics,
    private val getLatestPost: GetLatestPostUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _filters = MutableStateFlow(HomeFilters())
    val filters: StateFlow<HomeFilters> = _filters.asStateFlow()

    // Compte affiché dans la barre du haut ; null pour un visiteur.
    private val _account = MutableStateFlow<User?>(null)
    val account: StateFlow<User?> = _account.asStateFlow()

    private var loadJob: Job? = null

    // Bandeau sponsorisé en haut du fil (ADR-021) ; null pour un compte Premium, décidé par le serveur.
    private val _sponsoredAd = MutableStateFlow<VideoAd?>(null)
    val sponsoredAd: StateFlow<VideoAd?> = _sponsoredAd.asStateFlow()

    // Post le plus récent de la plateforme, inséré après les premières vidéos ; null sans post.
    private val _latestPost = MutableStateFlow<Post?>(null)
    val latestPost: StateFlow<Post?> = _latestPost.asStateFlow()

    // Publicité dont l'affichage a déjà été mesuré : une fois par chargement, pas à chaque défilement.
    private var shownAdId: String? = null

    /** Vidéo du bandeau, en boucle et en sourdine, lue seulement quand la carte est visible. */
    val adPreview = SponsoredAdPreview(context)

    init {
        // Premier chargement, puis à chaque changement des centres d'intérêt (onglet « Vous »),
        // pour remonter les bonnes vidéos en tête du fil.
        viewModelScope.launch {
            observeInterests().collect { load() }
        }
        viewModelScope.launch {
            observeSession().collect { loggedIn -> loadAccount(loggedIn) }
        }
        // Vidéo publiée depuis l'application : le fil la montre dès le retour sur l'accueil.
        viewModelScope.launch {
            observePublications().collect { load() }
        }
        // Abonnement Premium pris ou terminé : le bandeau disparaît ou revient.
        viewModelScope.launch {
            observePremium().map { it?.isPremium }.distinctUntilChanged().collect { loadSponsoredAd() }
        }
        loadCategories()
    }

    // Un échec laisse le fil sans bandeau.
    private fun loadSponsoredAd() {
        viewModelScope.launch {
            _sponsoredAd.value = try {
                getSponsoredAd()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            shownAdId = null
            adPreview.show(_sponsoredAd.value)
        }
    }

    /** La carte entre dans l'écran ou en sort : la vidéo suit, et l'affichage est mesuré une fois. */
    fun onSponsoredAdVisibilityChanged(visible: Boolean) {
        adPreview.setVisible(visible)
        val ad = _sponsoredAd.value ?: return
        if (!visible || shownAdId == ad.id) return
        shownAdId = ad.id
        analytics.track(AnalyticsEvent.SponsoredAdShown(ad.id))
    }

    /** Fil affiché ou quitté (autre onglet, autre écran, arrière-plan) : retour en sourdine en partant. */
    fun onFeedShown(shown: Boolean) = adPreview.setFeedShown(shown)

    fun onToggleSponsoredAdSound(ad: VideoAd) {
        if (adPreview.toggleMute()) analytics.track(AnalyticsEvent.SponsoredAdUnmuted(ad.id))
    }

    fun onSponsoredAdWatchClicked(ad: VideoAd) = analytics.track(AnalyticsEvent.SponsoredAdWatchClicked(ad.id))

    fun onSponsoredAdClicked(ad: VideoAd) = analytics.track(AnalyticsEvent.SponsoredAdClicked(ad.id, AdPlacement.HomeBanner))

    fun onRemoveAdsClicked(ad: VideoAd) = analytics.track(AnalyticsEvent.RemoveAdsClicked(ad.id))

    fun load() {
        loadJob?.cancel()
        _uiState.value = HomeUiState.Loading
        loadJob = viewModelScope.launch {
            _uiState.value = try {
                val videos = getHomeFeed(_filters.value.selectedCategoryId)
                if (videos.isEmpty()) HomeUiState.Empty else HomeUiState.Success(videos)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                HomeUiState.Error(e.toUiError())
            }
            // Premier chargement terminé, réussi ou non : l'animation d'ouverture peut s'effacer.
            firstScreenReadiness.markReady()
        }
        loadLatestPost()
    }

    // Un échec laisse le fil sans post.
    private fun loadLatestPost() {
        viewModelScope.launch {
            _latestPost.value = try {
                getLatestPost()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }

    /** null : « Tout ». */
    fun selectCategory(categoryId: String?) {
        if (categoryId == _filters.value.selectedCategoryId) return
        _filters.value = _filters.value.copy(selectedCategoryId = categoryId)
        load()
    }

    // Sans catégories (réseau, API), la rangée n'affiche que « Tout » : le fil reste utilisable.
    private fun loadCategories() {
        viewModelScope.launch {
            try {
                val categories = getCategories()
                _filters.value = _filters.value.copy(categories = categories)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    // Avatar de la barre du haut. En cas d'échec, on affiche l'icône de compte générique.
    private suspend fun loadAccount(loggedIn: Boolean) {
        _account.value = if (loggedIn) {
            try {
                getCurrentUser()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    override fun onCleared() = adPreview.release()
}
