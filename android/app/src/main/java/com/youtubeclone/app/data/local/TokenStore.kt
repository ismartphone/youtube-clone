package com.youtubeclone.app.data.local

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.MutablePreferences
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

@Serializable
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
)

/**
 * Compte enregistré sur l'appareil (ADR-022), sans ses tokens : ce que la feuille des comptes
 * affiche. Données personnelles mais pas secrètes, gardées dans le même fichier que les tokens.
 */
@Serializable
data class StoredAccount(
    val userId: String,
    val displayName: String = "",
    val email: String = "",
    /** @handle de la chaîne du compte ; null sans chaîne (le handle appartient à la chaîne). */
    val handle: String? = null,
    val avatarUrl: String? = null,
    /** Fin de validité du refresh token, en millisecondes ; 0 quand la session est terminée. */
    val refreshExpiresAt: Long = 0,
)

// Fichier exclu des sauvegardes Android (voir res/xml/backup_rules.xml).
private val Context.sessionDataStore by preferencesDataStore(name = "session")

private data class Session(
    val accounts: List<StoredAccount> = emptyList(),
    val activeUserId: String? = null,
    val tokens: AuthTokens? = null,
)

/**
 * Comptes enregistrés sur l'appareil et tokens du compte actif (ADR-022, qui remplace en partie
 * l'ADR-008). Les tokens de chaque compte sont chiffrés par [TokenCipher] avec sa propre clé,
 * dans sa propre entrée ; seuls ceux du compte actif sont déchiffrés et gardés en mémoire. Ceux
 * d'un autre compte ne sont lus qu'au moment d'une bascule vers lui. Rien n'est stocké en clair.
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext context: Context,
    private val cipher: TokenCipher,
    private val json: Json,
) {
    private val dataStore = context.sessionDataStore

    // Copie en mémoire : l'intercepteur lit le token à chaque requête sans déchiffrer le disque.
    private val session = MutableStateFlow(Session())
    private val loaded = CompletableDeferred<Unit>()
    // Les écritures (connexion, renouvellement, bascule, retrait) passent une par une.
    private val mutex = Mutex()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                session.value = readFromDisk()
            } finally {
                loaded.complete(Unit)
            }
        }
    }

    /** Tokens du compte actif, ou null hors session. Attend la lecture initiale du disque. */
    suspend fun get(): AuthTokens? {
        loaded.await()
        return session.value.tokens
    }

    /** Émet les tokens courants puis chaque changement : connexion, renouvellement, bascule, déconnexion. */
    val changes: Flow<AuthTokens?> = flow {
        loaded.await()
        emitAll(session)
    }.map { it.tokens }.distinctUntilChanged()

    /** Identifiant du compte actif, ou null hors session. */
    val userId: Flow<String?> = flow {
        loaded.await()
        emitAll(session)
    }.map { it.tokens?.let { _ -> it.activeUserId } }.distinctUntilChanged()

    /** Comptes enregistrés sur l'appareil, dans l'ordre d'ajout. */
    val accounts: Flow<List<StoredAccount>> = flow {
        loaded.await()
        emitAll(session)
    }.map { it.accounts }.distinctUntilChanged()

    /**
     * Enregistre les tokens de leur compte (champ "sub"), ajouté à la liste s'il n'y est pas.
     * [activate] : à la connexion, ce compte devient le compte actif. Au renouvellement, on ne
     * fait qu'enregistrer : si l'utilisateur a basculé entre-temps, le compte choisi reste actif.
     */
    suspend fun save(newTokens: AuthTokens, activate: Boolean = true) {
        loaded.await()
        val userId = subject(newTokens.accessToken) ?: return
        mutex.withLock {
            val current = session.value
            if (!activate && current.accounts.none { it.userId == userId }) return
            val refreshExpiresAt = expiresAt(newTokens.refreshToken)
            val accounts = current.accounts.upsert(userId) { it.copy(refreshExpiresAt = refreshExpiresAt) }
            val isActive = activate || current.activeUserId == userId
            val encrypted = cipher.encrypt(keyAlias(userId), json.encodeToString(newTokens))
            dataStore.edit {
                it[tokensKey(userId)] = encrypted
                it.writeAccounts(accounts)
                if (activate) it[ACTIVE_KEY] = userId
            }
            session.value = Session(
                accounts = accounts,
                activeUserId = if (activate) userId else current.activeUserId,
                tokens = if (isActive) newTokens else current.tokens,
            )
        }
    }

    /**
     * Termine la session du compte actif (refresh token refusé, compte introuvable) : ses tokens et
     * sa clé sont détruits, mais il reste dans la liste, « Reconnexion requise ». On devient visiteur.
     */
    suspend fun clear() {
        loaded.await()
        mutex.withLock {
            val userId = session.value.activeUserId ?: return
            endSession(userId)
            dataStore.edit { it.remove(ACTIVE_KEY) }
            session.update { it.copy(activeUserId = null, tokens = null) }
        }
    }

    /**
     * Fait de [userId] le compte actif, sans mot de passe. Renvoie false si sa session ne peut pas
     * être reprise (refresh token expiré, tokens illisibles) : il reste alors « Reconnexion requise ».
     */
    suspend fun activate(userId: String): Boolean {
        loaded.await()
        return mutex.withLock {
            val current = session.value
            if (current.accounts.none { it.userId == userId }) return@withLock false
            val tokens = readTokens(userId)
            if (tokens == null) {
                endSession(userId)
                return@withLock false
            }
            dataStore.edit { it[ACTIVE_KEY] = userId }
            session.value = current.copy(activeUserId = userId, tokens = tokens)
            true
        }
    }

    /** Retire [userId] de l'appareil : sa ligne, ses tokens et sa clé. Compte actif : on devient visiteur. */
    suspend fun remove(userId: String) {
        loaded.await()
        mutex.withLock {
            val current = session.value
            val accounts = current.accounts.filterNot { it.userId == userId }
            val wasActive = current.activeUserId == userId
            dataStore.edit {
                it.remove(tokensKey(userId))
                it.writeAccounts(accounts)
                if (wasActive) it.remove(ACTIVE_KEY)
            }
            runCatching { cipher.deleteKey(keyAlias(userId)) }
            session.value = if (wasActive) Session(accounts = accounts) else current.copy(accounts = accounts)
        }
    }

    /** Met à jour ce que la feuille des comptes affiche pour [userId] (nom, email, @handle, avatar). */
    suspend fun updateProfile(userId: String, displayName: String, email: String, handle: String?, avatarUrl: String?) {
        loaded.await()
        mutex.withLock {
            val current = session.value
            if (current.accounts.none { it.userId == userId }) return
            val accounts = current.accounts.upsert(userId) {
                it.copy(displayName = displayName, email = email, handle = handle, avatarUrl = avatarUrl)
            }
            if (accounts == current.accounts) return
            dataStore.edit { it.writeAccounts(accounts) }
            session.value = current.copy(accounts = accounts)
        }
    }

    // Tokens et clé détruits, compte gardé dans la liste avec une session terminée.
    private suspend fun endSession(userId: String) {
        val accounts = session.value.accounts.upsert(userId) { it.copy(refreshExpiresAt = 0) }
        dataStore.edit {
            it.remove(tokensKey(userId))
            it.writeAccounts(accounts)
        }
        runCatching { cipher.deleteKey(keyAlias(userId)) }
        session.update { it.copy(accounts = accounts) }
    }

    private suspend fun readFromDisk(): Session {
        migrateSingleSession()
        val prefs = runCatching { dataStore.data.first() }.getOrNull() ?: return Session()
        val accounts = prefs[ACCOUNTS_KEY]
            ?.let { runCatching { json.decodeFromString<List<StoredAccount>>(it) }.getOrNull() }
            .orEmpty()
        val activeUserId = prefs[ACTIVE_KEY]?.takeIf { id -> accounts.any { it.userId == id } }
        val tokens = activeUserId?.let { readTokens(it) }
        if (activeUserId != null && tokens == null) {
            // Illisible (clé du Keystore perdue) ou refresh token expiré : aucune session à reprendre.
            val ended = accounts.upsert(activeUserId) { it.copy(refreshExpiresAt = 0) }
            runCatching {
                dataStore.edit {
                    it.remove(tokensKey(activeUserId))
                    it.remove(ACTIVE_KEY)
                    it.writeAccounts(ended)
                }
            }
            return Session(accounts = ended)
        }
        return Session(accounts = accounts, activeUserId = activeUserId, tokens = tokens)
    }

    private suspend fun readTokens(userId: String): AuthTokens? {
        val encrypted = runCatching { dataStore.data.first()[tokensKey(userId)] }.getOrNull() ?: return null
        val stored = runCatching {
            json.decodeFromString<AuthTokens>(cipher.decrypt(keyAlias(userId), encrypted))
        }.getOrNull()
        return stored?.takeUnless { isExpired(it.refreshToken) }
    }

    /**
     * Avant l'ADR-022, une seule entrée « tokens » chiffrée avec la clé « session_tokens ». Au
     * premier démarrage après la mise à jour, elle devient le premier compte enregistré, avec sa
     * propre clé ; l'ancienne entrée et l'ancienne clé sont supprimées.
     */
    private suspend fun migrateSingleSession() {
        val legacy = runCatching { dataStore.data.first()[LEGACY_TOKENS_KEY] }.getOrNull() ?: return
        val tokens = runCatching {
            json.decodeFromString<AuthTokens>(cipher.decrypt(LEGACY_KEY_ALIAS, legacy))
        }.getOrNull()?.takeUnless { isExpired(it.refreshToken) }
        val userId = tokens?.let { subject(it.accessToken) }
        runCatching {
            dataStore.edit {
                it.remove(LEGACY_TOKENS_KEY)
                if (tokens != null && userId != null) {
                    it[tokensKey(userId)] = cipher.encrypt(keyAlias(userId), json.encodeToString(tokens))
                    it.writeAccounts(listOf(StoredAccount(userId, refreshExpiresAt = expiresAt(tokens.refreshToken))))
                    it[ACTIVE_KEY] = userId
                }
            }
        }
        runCatching { cipher.deleteKey(LEGACY_KEY_ALIAS) }
    }

    private fun MutablePreferences.writeAccounts(accounts: List<StoredAccount>) {
        this[ACCOUNTS_KEY] = json.encodeToString(accounts)
    }

    private fun List<StoredAccount>.upsert(userId: String, change: (StoredAccount) -> StoredAccount): List<StoredAccount> =
        if (any { it.userId == userId }) map { if (it.userId == userId) change(it) else it }
        else this + change(StoredAccount(userId))

    private fun isExpired(jwt: String): Boolean = expiresAt(jwt) <= System.currentTimeMillis()

    private fun expiresAt(jwt: String): Long = runCatching {
        claims(jwt).getValue("exp").jsonPrimitive.long * 1000
    }.getOrDefault(0)

    private fun subject(jwt: String): String? = runCatching {
        claims(jwt).getValue("sub").jsonPrimitive.content
    }.getOrNull()

    // Lit le contenu du token sans vérifier la signature : seul le serveur en est capable.
    private fun claims(jwt: String): JsonObject {
        val payload = jwt.split('.')[1]
        val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        return json.parseToJsonElement(String(decoded, Charsets.UTF_8)).jsonObject
    }

    private companion object {
        val ACCOUNTS_KEY = stringPreferencesKey("accounts")
        val ACTIVE_KEY = stringPreferencesKey("active_account")
        val LEGACY_TOKENS_KEY = stringPreferencesKey("tokens")
        const val LEGACY_KEY_ALIAS = "session_tokens"

        fun tokensKey(userId: String) = stringPreferencesKey("tokens_$userId")
        fun keyAlias(userId: String) = "session_tokens_$userId"
    }
}
