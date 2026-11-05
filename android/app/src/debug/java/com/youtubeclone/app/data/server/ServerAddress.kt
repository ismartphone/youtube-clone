package com.youtubeclone.app.data.server

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.youtubeclone.app.BuildConfig
import com.youtubeclone.app.data.repository.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Adresse de l'API (ADR-027), version debug : l'adresse réglée dans Paramètres, ou à défaut la
 * constante de compilation. Elle est lue une seule fois, au démarrage ([initialize]) : un
 * changement n'est donc pris en compte qu'après le redémarrage de l'application.
 */
object ServerAddress {
    /** Adresse par défaut, issue de `apiBaseUrl` dans gradle.properties. */
    val defaultUrl: String = BuildConfig.API_BASE_URL

    @Volatile
    private var current: String = defaultUrl

    /** Adresse utilisée par l'API, le temps réel et les médias, jusqu'au prochain démarrage. */
    val baseUrl: String get() = current

    private val KEY = stringPreferencesKey("server_base_url")

    /**
     * À appeler avant toute création du client réseau. Lecture bloquante, une fois : le
     * DataStore doit être lu avant que Retrofit ne construise ses clients.
     */
    fun initialize(context: Context) {
        val saved = runCatching {
            runBlocking { context.preferencesDataStore.data.first()[KEY] }
        }.getOrNull()
        current = saved?.takeIf { it.isNotBlank() } ?: defaultUrl
    }

    /** Enregistre [url] pour le prochain démarrage ; null rétablit l'adresse par défaut. */
    suspend fun save(context: Context, url: String?) {
        context.preferencesDataStore.edit {
            if (url == null || url == defaultUrl) it.remove(KEY) else it[KEY] = url
        }
    }
}

/**
 * Adresse saisie, normalisée : schéma http ou https obligatoire, hôte présent, terminée par « / ».
 * Renvoie null si elle est invalide.
 */
fun normalizeServerAddress(input: String): String? {
    val text = input.trim()
    val match = Regex("""^(https?)://([^/\s?#]+)(/[^\s?#]*)?$""", RegexOption.IGNORE_CASE).matchEntire(text)
        ?: return null
    val host = match.groupValues[2]
    // L'hôte ne doit pas être vide (« http://:3000 ») ni contenir d'identifiants.
    if (host.startsWith(":") || host.contains("@")) return null
    return text.trimEnd('/') + "/"
}
