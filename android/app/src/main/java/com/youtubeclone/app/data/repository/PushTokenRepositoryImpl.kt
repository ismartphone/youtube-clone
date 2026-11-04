package com.youtubeclone.app.data.repository

import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import com.youtubeclone.app.data.remote.DeviceApi
import com.youtubeclone.app.data.remote.apiCall
import com.youtubeclone.app.data.remote.dto.DeviceTokenDto
import com.youtubeclone.app.domain.repository.PushTokenRepository
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

class PushTokenRepositoryImpl @Inject constructor(
    private val api: DeviceApi,
    private val json: Json,
) : PushTokenRepository {

    override suspend fun register(token: String?) {
        val current = token ?: FirebaseMessaging.getInstance().token.await()
        apiCall(json) { api.register(DeviceTokenDto(current)) }
    }

    override suspend fun unregister() {
        val messaging = FirebaseMessaging.getInstance()
        // La déconnexion n'attend pas le réseau plus de quelques secondes.
        withTimeoutOrNull(UNREGISTER_TIMEOUT_MS) {
            try {
                val token = messaging.token.await()
                apiCall(json) { api.unregister(token) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Hors ligne : la suppression locale ci-dessous suffit, le serveur oubliera le
                // jeton au premier envoi refusé par Firebase.
            }
        }
        runCatching { messaging.deleteToken().await() }
    }

    private companion object {
        const val UNREGISTER_TIMEOUT_MS = 5_000L
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        val error = task.exception
        if (error != null) continuation.resumeWithException(error) else continuation.resume(task.result)
    }
}
