package com.youtubeclone.app.presentation.settings

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeclone.app.data.server.ServerAddress
import com.youtubeclone.app.data.server.normalizeServerAddress
import com.youtubeclone.app.domain.repository.AuthRepository
import com.youtubeclone.app.restart.restartApp
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Résultat du test de connexion. */
sealed interface ConnectionTest {
    data object Idle : ConnectionTest
    data object Running : ConnectionTest
    data object Ok : ConnectionTest
    data class HttpError(val code: Int) : ConnectionTest
    data class Failed(val reason: String) : ConnectionTest
}

data class ServerAddressUiState(
    val input: String,
    val invalid: Boolean = false,
    val test: ConnectionTest = ConnectionTest.Idle,
    val saving: Boolean = false,
)

// Route publique de l'API, appelée sans session : le test n'a besoin d'aucun compte.
private const val TEST_PATH = "categories"
private const val TEST_TIMEOUT_SECONDS = 6L
private const val LOG_TAG = "ServerAddress"

// Le détail technique va au journal ; l'écran affiche une phrase française fixe.
private fun failed(e: Exception): ConnectionTest.Failed {
    Log.w(LOG_TAG, "Connexion impossible", e)
    return ConnectionTest.Failed(e.localizedMessage ?: e.javaClass.simpleName)
}

/**
 * Réglage de l'adresse du serveur (ADR-027, debug). Le test de connexion a son propre client,
 * indépendant de ceux de l'application. Enregistrer déconnecte tous les comptes et redémarre :
 * l'adresse n'est lue qu'au démarrage.
 */
@HiltViewModel
class ServerAddressViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
) : ViewModel() {

    val currentUrl: String = ServerAddress.baseUrl
    val defaultUrl: String = ServerAddress.defaultUrl

    private val _state = MutableStateFlow(ServerAddressUiState(input = currentUrl))
    val state: StateFlow<ServerAddressUiState> = _state.asStateFlow()

    private val testClient = OkHttpClient.Builder()
        .callTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    fun onInputChange(value: String) {
        _state.update { it.copy(input = value, invalid = false, test = ConnectionTest.Idle) }
    }

    /** Remet l'adresse par défaut dans le champ ; elle ne s'applique qu'à l'enregistrement. */
    fun useDefault() = onInputChange(defaultUrl)

    fun testConnection() {
        val url = normalizeServerAddress(_state.value.input)
        if (url == null) {
            _state.update { it.copy(invalid = true) }
            return
        }
        _state.update { it.copy(test = ConnectionTest.Running) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    testClient.newCall(Request.Builder().url(url + TEST_PATH).build()).execute().use { response ->
                        if (response.isSuccessful) ConnectionTest.Ok else ConnectionTest.HttpError(response.code)
                    }
                } catch (e: IOException) {
                    failed(e)
                } catch (e: IllegalArgumentException) {
                    failed(e)
                }
            }
            _state.update { it.copy(test = result) }
        }
    }

    /**
     * Enregistre l'adresse, déconnecte tous les comptes (jetons et clés effacés, liste vidée) puis
     * redémarre. Les téléchargements ne sont pas touchés : on n'appelle pas le retrait de compte de
     * l'ADR-022, qui les supprime, mais seulement celui du dépôt d'authentification.
     */
    fun save() {
        val url = normalizeServerAddress(_state.value.input)
        if (url == null) {
            _state.update { it.copy(invalid = true) }
            return
        }
        if (url == currentUrl) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                authRepository.accounts.first().forEach { authRepository.removeAccount(it.userId) }
                ServerAddress.save(context, url)
                restartApp(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, test = failed(e)) }
            }
        }
    }
}
