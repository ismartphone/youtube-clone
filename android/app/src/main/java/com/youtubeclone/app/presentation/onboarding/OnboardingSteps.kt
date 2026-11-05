package com.youtubeclone.app.presentation.onboarding

import androidx.annotation.StringRes
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.VideoFormat

// Parcours d'accueil (ADR-028) : une liste d'étapes typées, affichée par UN seul composant
// (OnboardingScreen). Ajouter une étape d'un type existant = ajouter une ligne à ONBOARDING_STEPS,
// avec ses textes dans strings.xml : aucun fichier, aucune route.

/** Clé sous laquelle la réponse d'une étape de choix est rangée, puis lue par le récapitulatif. */
enum class AnswerKey { INTERESTS, PREFERRED_FORMAT }

/** Action nommée d'une étape spéciale ; la règle métier n'est pas dans le composant d'affichage. */
enum class StepAction { REQUEST_NOTIFICATIONS, REGISTER, PAYWALL }

/** Visuel central d'une étape de présentation. */
enum class StepVisual { LOGO, VIDEO_MOSAIC, SHORTS_ROW, NOTIFICATIONS }

/** Option d'un choix unique : [value] est la valeur rangée, [label] le texte affiché. */
data class ChoiceOption(val value: String, @StringRes val label: Int)

sealed interface StepKind {
    /** Écran de présentation : visuel, titre, texte. */
    data class Presentation(val visual: StepVisual) : StepKind

    /** Choix multiple parmi les catégories de l'API, [minChoices] au minimum. */
    data class MultiChoice(val key: AnswerKey, val minChoices: Int) : StepKind

    /** Choix unique entre [options]. */
    data class SingleChoice(val key: AnswerKey, val options: List<ChoiceOption>) : StepKind

    /** Chargement animé : avance seul au bout de [durationMs]. */
    data class Loading(val durationMs: Long) : StepKind

    /** Récapitulatif des réponses [keys]. */
    data class Recap(val keys: List<AnswerKey>) : StepKind

    /** Écran standard (visuel, titre, texte) ou écran existant, avec une action nommée. */
    data class Action(val action: StepAction, val visual: StepVisual? = null) : StepKind
}

/**
 * Une étape. [id] est son nom stable dans les mesures. [showNext] : bouton « Suivant » ;
 * [skipLabel] : libellé du lien discret (« Passer », « Plus tard »), null sans lien. Les écrans
 * qui portent leurs propres boutons (inscription, paywall) masquent ceux du composant.
 */
data class OnboardingStep(
    val id: String,
    val kind: StepKind,
    @StringRes val title: Int,
    @StringRes val text: Int? = null,
    val showNext: Boolean = true,
    @StringRes val skipLabel: Int? = R.string.onboarding_skip,
)

const val LOADING_STEP_MS = 2_000L

val ONBOARDING_STEPS: List<OnboardingStep> = listOf(
    OnboardingStep("welcome", StepKind.Presentation(StepVisual.LOGO), R.string.onboarding_welcome_title, R.string.onboarding_welcome_text),
    OnboardingStep(
        "millions_of_videos", StepKind.Presentation(StepVisual.VIDEO_MOSAIC),
        R.string.onboarding_videos_title, R.string.onboarding_videos_text,
    ),
    OnboardingStep(
        "scrolling_shorts", StepKind.Presentation(StepVisual.SHORTS_ROW),
        R.string.onboarding_shorts_title, R.string.onboarding_shorts_text,
    ),
    OnboardingStep(
        "interests", StepKind.MultiChoice(AnswerKey.INTERESTS, minChoices = 3),
        R.string.onboarding_interests_title, R.string.onboarding_interests_text,
    ),
    OnboardingStep(
        "format",
        StepKind.SingleChoice(
            AnswerKey.PREFERRED_FORMAT,
            listOf(
                ChoiceOption(VideoFormat.LONG.name, R.string.onboarding_format_videos),
                ChoiceOption(VideoFormat.SHORT.name, R.string.onboarding_format_shorts),
            ),
        ),
        R.string.onboarding_format_title, R.string.onboarding_format_text,
    ),
    OnboardingStep(
        "preparing_feed", StepKind.Loading(LOADING_STEP_MS), R.string.onboarding_loading_title,
        showNext = false, skipLabel = null,
    ),
    OnboardingStep(
        "feed_ready", StepKind.Recap(listOf(AnswerKey.INTERESTS, AnswerKey.PREFERRED_FORMAT)),
        R.string.onboarding_recap_title, R.string.onboarding_recap_text,
    ),
    OnboardingStep(
        "notifications", StepKind.Action(StepAction.REQUEST_NOTIFICATIONS, StepVisual.NOTIFICATIONS),
        R.string.onboarding_notifications_title, R.string.onboarding_notifications_text,
    ),
    OnboardingStep(
        "account", StepKind.Action(StepAction.REGISTER), R.string.onboarding_account_title,
        showNext = false, skipLabel = R.string.onboarding_later,
    ),
    OnboardingStep(
        "paywall", StepKind.Action(StepAction.PAYWALL), R.string.onboarding_paywall_title,
        showNext = false, skipLabel = null,
    ),
)
