package com.youtubeclone.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.youtubeclone.app.domain.usecase.UnreadMessages
import com.youtubeclone.app.notifications.MessageNotifications
import com.youtubeclone.app.notifications.PublicationNotifications
import com.youtubeclone.app.presentation.navigation.AppRoot
import com.youtubeclone.app.presentation.theme.YoutubeCloneTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var unreadMessages: UnreadMessages

    // Vidéo à ouvrir, venue d'un appui sur une notification de publication (ADR-020).
    private var notifiedVideoId by mutableStateOf<String?>(null)

    // Conversation à ouvrir, venue d'un appui sur une notification de message (ADR-025).
    private var notifiedConversationId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Écran de démarrage natif à fond sombre (Theme.App.Starting), avant la première image de Compose.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Thème toujours sombre : icônes claires dans les barres système, quel que soit le réglage du téléphone.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // Une recréation (rotation...) ne rouvre pas la vidéo déjà ouverte.
        if (savedInstanceState == null) {
            notifiedVideoId = intent.notifiedVideoId()
            notifiedConversationId = intent.notifiedConversationId()
        }
        setContent {
            YoutubeCloneTheme {
                AppRoot(
                    notifiedVideoId = notifiedVideoId,
                    onNotifiedVideoOpened = { notifiedVideoId = null },
                    notifiedConversationId = notifiedConversationId,
                    onNotifiedConversationOpened = { notifiedConversationId = null },
                )
            }
        }
    }

    // Retour au premier plan : la pastille des messages non lus est relue (ADR-025, décision 2).
    override fun onStart() {
        super.onStart()
        unreadMessages.refresh()
    }

    // Application déjà ouverte : la notification revient ici.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.notifiedVideoId()?.let { notifiedVideoId = it }
        intent.notifiedConversationId()?.let { notifiedConversationId = it }
    }

    private fun Intent.notifiedVideoId(): String? = getStringExtra(PublicationNotifications.EXTRA_VIDEO_ID)

    private fun Intent.notifiedConversationId(): String? = getStringExtra(MessageNotifications.EXTRA_CONVERSATION_ID)
}
