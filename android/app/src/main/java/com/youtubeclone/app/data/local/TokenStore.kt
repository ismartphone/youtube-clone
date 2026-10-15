package com.youtubeclone.app.data.local

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

@Serializable
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
)

// Fichier exclu des sauvegardes Android (voir res/xml/backup_rules.xml).
private val Context.sessionDataStore by preferencesDataStore(name = "session")

/**
 * Tokens de la session, chiffrés par [TokenCipher] avant d'être écrits dans DataStore.
 * Rien n'est jamais stocké en clair.
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext context: Context,
    private val cipher: TokenCipher,
    private val json: Json,
) {
    private val dataStore = context.sessionDataStore

    // Copie en mémoire : l'intercepteur lit le token à chaque requête sans déchiffrer le disque.
    private val tokens = MutableStateFlow<AuthTokens?>(null)
    private val loaded = CompletableDeferred<Unit>()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                tokens.value = readFromDisk()
            } finally {
                loaded.complete(Unit)
            }
        }
    }

    /** Tokens courants, ou null hors session. Attend la lecture initiale du disque. */
    suspend fun get(): AuthTokens? {
        loaded.await()
        return tokens.value
    }

    /** Émet les tokens courants puis chaque changement : connexion, renouvellement, déconnexion. */
    val changes: Flow<AuthTokens?> = flow {
        loaded.await()
        emitAll(tokens)
    }

    suspend fun save(newTokens: AuthTokens) {
        loaded.await()
        val encrypted = cipher.encrypt(json.encodeToString(newTokens))
        dataStore.edit { it[TOKENS_KEY] = encrypted }
        tokens.value = newTokens
    }

    suspend fun clear() {
        loaded.await()
        tokens.value = null
        dataStore.edit { it.remove(TOKENS_KEY) }
    }

    private suspend fun readFromDisk(): AuthTokens? {
        val encrypted = runCatching { dataStore.data.first()[TOKENS_KEY] }.getOrNull() ?: return null
        val stored = runCatching { json.decodeFromString<AuthTokens>(cipher.decrypt(encrypted)) }.getOrNull()
        // Illisible (clé du Keystore perdue) ou refresh token expiré : aucune session à reprendre.
        if (stored == null || isExpired(stored.refreshToken)) {
            runCatching { dataStore.edit { it.remove(TOKENS_KEY) } }
            return null
        }
        return stored
    }

    // Lit la date d'expiration (champ "exp") sans vérifier la signature : seul le serveur en est capable.
    private fun isExpired(jwt: String): Boolean = runCatching {
        val payload = jwt.split('.')[1]
        val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val exp = json.parseToJsonElement(String(decoded, Charsets.UTF_8)).jsonObject.getValue("exp").jsonPrimitive.long
        exp * 1000 <= System.currentTimeMillis()
    }.getOrDefault(true)

    private companion object {
        val TOKENS_KEY = stringPreferencesKey("tokens")
    }
}
