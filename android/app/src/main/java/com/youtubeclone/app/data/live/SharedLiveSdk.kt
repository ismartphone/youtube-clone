package com.youtubeclone.app.data.live

import com.youtubeclone.app.BuildConfig
import com.youtubeclone.app.data.remote.SharedLiveApi
import fr.echimine.live.core.LiveSdk
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Configuration du SDK du live partagé (ADR-031). Faite à la première ouverture des Lives partagés,
 * pas au démarrage : configurer charge aussitôt la config du serveur commun avec un Jeton de
 * Participant, donc envoie le nom de l'utilisateur à un tiers. Personne n'est annoncé au serveur
 * commun sans être allé dans les Lives partagés.
 */
@Singleton
class SharedLiveSdk @Inject constructor(private val api: SharedLiveApi) {

    private var configured = false

    @Synchronized
    fun ensureConfigured() {
        if (configured) return
        LiveSdk.configure(
            serverUrl = BuildConfig.LIVE_SERVER_URL,
            // Appelé par le SDK à chaque connexion et reconnexion : un jeton frais à chaque fois,
            // jamais gardé. Il suit donc le compte actif après un changement de compte (ADR-022).
            // Session expirée : TokenAuthenticator rafraîchit l'access token et rejoue l'appel.
            tokenProvider = { api.participantToken().token },
        )
        configured = true
    }
}
