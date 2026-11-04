package com.youtubeclone.app.data.analytics

import android.content.Context
import com.posthog.PersonProfiles
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import com.youtubeclone.app.BuildConfig
import com.youtubeclone.app.domain.analytics.Analytics
import com.youtubeclone.app.domain.analytics.AnalyticsEvent
import com.youtubeclone.app.domain.analytics.Experiments
import com.youtubeclone.app.domain.analytics.PaywallVariant
import com.youtubeclone.app.domain.repository.AuthRepository
import com.youtubeclone.app.domain.repository.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Mesures envoyées directement à PostHog cloud Europe (ADR-006), sans passer par le backend.
 * Respecte l'ADR-013 : compte identifié par son seul UUID, visiteur anonyme, et aucun envoi
 * quand « Partager des statistiques d'usage » est désactivé.
 * Fournit aussi la variante du test A/B du paywall, lue dans les drapeaux PostHog.
 */
@Singleton
class PostHogAnalytics @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: PreferencesRepository,
    private val auth: AuthRepository,
) : Analytics, Experiments {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Sans clé (secrets.properties absent), rien n'est initialisé et chaque appel est ignoré.
    private val enabled = BuildConfig.POSTHOG_API_KEY.isNotBlank()

    // Le SDK n'est initialisé qu'avec le partage activé : refusé dès le lancement, aucune requête
    // ne part vers PostHog, pas même la lecture de sa configuration.
    @Volatile private var isSetUp = false

    /** À appeler une fois, au démarrage de l'application. */
    fun start() {
        if (!enabled) return
        // Lu avant l'initialisation : un refus enregistré doit bloquer jusqu'à l'événement
        // d'ouverture de l'application, envoyé dès le démarrage.
        if (runBlocking { preferences.shareUsageStats.first() }) setUp()

        scope.launch {
            preferences.shareUsageStats.drop(1).collect { share ->
                when {
                    share && !isSetUp -> {
                        setUp()
                        // Activé en cours de session : la session ouverte n'a pas encore été identifiée.
                        auth.currentUserId.first()?.let(::identify)
                    }
                    share -> PostHog.optIn()
                    isSetUp -> PostHog.optOut()
                }
            }
        }
        // Session reprise au démarrage ou expirée : identification ou retour à l'anonymat.
        // La connexion et la déconnexion explicites passent aussi par les cas d'usage, pour que
        // leurs événements soient rattachés au bon identifiant.
        scope.launch {
            var previous: String? = null
            auth.currentUserId.collect { userId ->
                // Bascule d'un compte à l'autre (ADR-022) : reset d'abord, sinon les événements
                // suivants risquent de rester rattachés à l'ancien identifiant.
                if (previous != null && userId != previous) reset()
                if (userId != null) identify(userId)
                previous = userId
            }
        }
    }

    @Synchronized
    private fun setUp() {
        if (isSetUp) return
        val config = PostHogAndroidConfig(BuildConfig.POSTHOG_API_KEY, BuildConfig.POSTHOG_HOST).apply {
            // Ouverture et mise en arrière-plan de l'application (« Application Opened »).
            captureApplicationLifecycleEvents = true
            // Une seule activité : les écrans Compose sont envoyés par screen(), pas automatiquement.
            captureScreenViews = false
            captureDeepLinks = false
            sessionReplay = false
            // Pas de profil pour un visiteur : il reste anonyme tant qu'il ne se connecte pas.
            personProfiles = PersonProfiles.IDENTIFIED_ONLY
            // Drapeaux (test A/B du paywall) chargés dès le démarrage, puis gardés en cache
            // sur l'appareil pour les lancements suivants.
            preloadFeatureFlags = true
            debug = BuildConfig.DEBUG
        }
        PostHogAndroid.setup(context, config)
        // Optin explicite : un refus d'une session précédente reste mémorisé par le SDK.
        PostHog.optIn()
        isSetUp = true
    }

    // Avant l'initialisation, ou sans clé, chaque appel est ignoré.

    override fun track(event: AnalyticsEvent) {
        if (isSetUp) PostHog.capture(event = event.name, properties = event.properties)
    }

    override fun screen(name: String) {
        if (isSetUp) PostHog.screen(screenTitle = name)
    }

    override fun identify(userId: String) {
        if (isSetUp && PostHog.distinctId() != userId) PostHog.identify(distinctId = userId)
    }

    override fun reset() {
        if (isSetUp) PostHog.reset()
    }

    // Lu dans le cache du SDK, sans attendre le réseau. La lecture envoie aussi l'exposition
    // au test ($feature_flag_called), utilisée par PostHog pour compter les participants.
    override fun paywallVariant(): PaywallVariant =
        if (isSetUp) PaywallVariant.fromKey(PostHog.getFeatureFlag(PaywallVariant.FLAG_KEY) as? String) else PaywallVariant.BENEFITS
}
