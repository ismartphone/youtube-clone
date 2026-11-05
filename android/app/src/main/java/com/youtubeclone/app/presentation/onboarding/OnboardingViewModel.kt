package com.youtubeclone.app.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeclone.app.domain.analytics.Analytics
import com.youtubeclone.app.domain.analytics.AnalyticsEvent
import com.youtubeclone.app.domain.model.Category
import com.youtubeclone.app.domain.model.Video
import com.youtubeclone.app.domain.model.VideoFormat
import com.youtubeclone.app.domain.usecase.CompleteOnboardingUseCase
import com.youtubeclone.app.domain.usecase.GetCategoriesUseCase
import com.youtubeclone.app.domain.usecase.GetVideosUseCase
import com.youtubeclone.app.domain.usecase.ObserveInterestsUseCase
import com.youtubeclone.app.domain.usecase.ObservePreferredFormatUseCase
import com.youtubeclone.app.domain.usecase.SaveInterestsUseCase
import com.youtubeclone.app.domain.usecase.SetPreferredFormatUseCase
import com.youtubeclone.app.presentation.common.UiError
import com.youtubeclone.app.presentation.common.toUiError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface OnboardingCategories {
    data object Loading : OnboardingCategories
    data class Error(val error: UiError) : OnboardingCategories
    data class Loaded(val categories: List<Category>) : OnboardingCategories
}

data class OnboardingUiState(
    /** Position de l'étape courante dans [ONBOARDING_STEPS]. */
    val index: Int = 0,
    /** Sens du dernier changement d'étape, pour la transition glissée. */
    val forward: Boolean = true,
    /** Change à chaque entrée dans une étape : recrée les écrans hébergés (inscription, paywall). */
    val visit: Int = 0,
    val categories: OnboardingCategories = OnboardingCategories.Loading,
    val selectedInterests: Set<String> = emptySet(),
    val preferredFormat: VideoFormat? = null,
    /** Vidéos classiques et Shorts du catalogue, pour les visuels des étapes 2 et 3 (D10) ; vides sans réseau. */
    val longVideos: List<Video> = emptyList(),
    val shorts: List<Video> = emptyList(),
) {
    val step: OnboardingStep get() = ONBOARDING_STEPS[index]
}

/**
 * Parcours d'accueil (ADR-028). Garde la position, les réponses et le catalogue d'aperçu ; enregistre
 * chaque choix à la validation de son étape ; envoie les mesures de l'entonnoir.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val completeOnboarding: CompleteOnboardingUseCase,
    private val getCategories: GetCategoriesUseCase,
    private val getVideos: GetVideosUseCase,
    private val observeInterests: ObserveInterestsUseCase,
    private val saveInterests: SaveInterestsUseCase,
    observePreferredFormat: ObservePreferredFormatUseCase,
    private val setPreferredFormat: SetPreferredFormatUseCase,
    private val analytics: Analytics,
    private val feedback: OnboardingFeedback,
    firstScreenReadiness: FirstScreenReadiness,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    // Dernière étape vue, pour onboarding_abandoned.
    private var lastViewedIndex = 0
    private var finished = false

    // Choix déjà fait par l'utilisateur : la lecture des choix enregistrés ne doit pas l'écraser.
    private var changedInterests = false
    private var changedFormat = false

    init {
        feedback.warmUp()
        // Rien à charger sous l'animation d'ouverture : la première étape est locale.
        firstScreenReadiness.markReady()
        analytics.track(AnalyticsEvent.OnboardingStarted)
        trackViewed(0)
        // Choix déjà enregistrés (parcours interrompu) : cochés d'avance.
        viewModelScope.launch {
            val saved = observeInterests().first()
            if (!changedInterests) _uiState.update { it.copy(selectedInterests = saved) }
            val format = observePreferredFormat().first()
            if (!changedFormat) _uiState.update { it.copy(preferredFormat = format) }
        }
        loadCategories()
        loadCatalogue()
    }

    fun loadCategories() {
        _uiState.update { it.copy(categories = OnboardingCategories.Loading) }
        viewModelScope.launch {
            val result = try {
                OnboardingCategories.Loaded(getCategories())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                OnboardingCategories.Error(e.toUiError())
            }
            _uiState.update { it.copy(categories = result) }
        }
    }

    // Catalogue d'aperçu des étapes 2 et 3 : un échec laisse les listes vides, les visuels de repli s'affichent.
    private fun loadCatalogue() {
        viewModelScope.launch {
            val long = async { loadVideos(VideoFormat.LONG, LONG_VIDEOS) }
            val short = async { loadVideos(VideoFormat.SHORT, SHORTS) }
            _uiState.update { it.copy(longVideos = long.await(), shorts = short.await()) }
        }
    }

    private suspend fun loadVideos(format: VideoFormat, count: Int): List<Video> = try {
        getVideos(format = format, pageSize = count).items
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

    fun toggleInterest(categoryId: String) {
        changedInterests = true
        feedback.choice()
        _uiState.update {
            val selected = if (categoryId in it.selectedInterests) it.selectedInterests - categoryId else it.selectedInterests + categoryId
            it.copy(selectedInterests = selected)
        }
    }

    fun selectFormat(format: VideoFormat) {
        changedFormat = true
        feedback.choice()
        _uiState.update { it.copy(preferredFormat = format) }
    }

    /** « Suivant » : valide l'étape (enregistre son choix) puis passe à la suivante. */
    fun next() {
        feedback.tap()
        val state = _uiState.value
        when (val kind = state.step.kind) {
            is StepKind.MultiChoice -> if (kind.key == AnswerKey.INTERESTS) saveInterestsChoice(state)
            is StepKind.SingleChoice -> if (kind.key == AnswerKey.PREFERRED_FORMAT) {
                viewModelScope.launch { setPreferredFormat(state.preferredFormat) }
            }
            else -> Unit
        }
        advance()
    }

    /** « Passer » (ou « Plus tard ») : saute l'étape sans enregistrer de choix. */
    fun skip() {
        feedback.tap()
        val state = _uiState.value
        analytics.track(AnalyticsEvent.OnboardingStepSkipped(state.index + 1, state.step.id))
        advance()
    }

    /**
     * Fin de l'étape de chargement, ou d'un écran hébergé qui a abouti (inscription, achat). Ignoré si
     * l'utilisateur a déjà quitté l'étape [fromStepId] (retour pendant l'animation).
     */
    fun advanceAutomatically(fromStepId: String) {
        if (_uiState.value.step.id == fromStepId) advance()
    }

    fun back() {
        val state = _uiState.value
        if (state.index == 0) return
        feedback.tap()
        // L'étape de chargement ne se rejoue pas en reculant : on passe au-dessus.
        var target = state.index - 1
        while (target > 0 && ONBOARDING_STEPS[target].kind is StepKind.Loading) target--
        goTo(target, forward = false)
    }

    // Une catégorie supprimée depuis le dernier choix n'est pas conservée.
    private fun saveInterestsChoice(state: OnboardingUiState) {
        val known = (state.categories as? OnboardingCategories.Loaded)?.categories?.mapTo(HashSet()) { it.id }
        val selected = if (known != null) state.selectedInterests intersect known else state.selectedInterests
        viewModelScope.launch { saveInterests(selected) }
    }

    private fun advance() {
        val state = _uiState.value
        if (state.index == ONBOARDING_STEPS.lastIndex) finish() else goTo(state.index + 1, forward = true)
    }

    private fun goTo(index: Int, forward: Boolean) {
        feedback.step()
        _uiState.update { it.copy(index = index, forward = forward, visit = it.visit + 1) }
        trackViewed(index)
    }

    private fun trackViewed(index: Int) {
        lastViewedIndex = index
        analytics.track(AnalyticsEvent.OnboardingStepViewed(index + 1, ONBOARDING_STEPS[index].id))
    }

    // Le parcours est terminé quand on sort de la dernière étape, par n'importe quel chemin (D9).
    private fun finish() {
        if (finished) return
        finished = true
        feedback.success()
        analytics.track(AnalyticsEvent.OnboardingCompleted)
        viewModelScope.launch { completeOnboarding() }
    }

    /** L'application passe en arrière-plan : si le parcours n'est pas terminé, il est compté comme abandonné (D4). */
    fun onBackgrounded() {
        if (finished) return
        analytics.track(AnalyticsEvent.OnboardingAbandoned(lastViewedIndex + 1, ONBOARDING_STEPS[lastViewedIndex].id))
    }

    private companion object {
        const val LONG_VIDEOS = 12
        const val SHORTS = 6
    }
}
