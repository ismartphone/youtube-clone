package com.youtubeclone.app.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.youtubeclone.app.domain.repository.PushTokenRepository
import com.youtubeclone.app.domain.usecase.ObserveSessionUseCase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Réception FCM (ADR-020), application ouverte ou fermée : le serveur envoie un message de données,
 * transformé ici en notification. Un jeton renouvelé est réenregistré si une session est ouverte.
 */
@AndroidEntryPoint
class PublicationMessagingService : FirebaseMessagingService() {

    @Inject lateinit var pushTokens: PushTokenRepository
    @Inject lateinit var observeSession: ObserveSessionUseCase

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            if (observeSession().first()) runCatching { pushTokens.register(token) }
        }
    }

    // Appelée sur un fil d'arrière-plan, qui peut attendre la miniature.
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["type"] != TYPE_VIDEO_PUBLISHED) return
        val videoId = data["videoId"] ?: return
        val alert = PublicationAlert(
            videoId = videoId,
            channelName = data["channelName"].orEmpty(),
            title = data["title"].orEmpty(),
            thumbnailUrl = data["thumbnailUrl"]?.takeIf { it.isNotEmpty() },
        )
        runBlocking { PublicationNotifications.show(applicationContext, alert) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TYPE_VIDEO_PUBLISHED = "video_published"
    }
}
