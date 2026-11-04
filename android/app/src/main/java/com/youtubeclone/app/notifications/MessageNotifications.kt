package com.youtubeclone.app.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.youtubeclone.app.MainActivity
import com.youtubeclone.app.R

/** Un message reçu, tel qu'annoncé par le serveur (ADR-025, étape 3). [text] null : vidéo partagée. */
data class MessageAlert(
    val conversationId: String,
    val senderName: String,
    val text: String?,
)

/**
 * Notification d'un message reçu (ADR-025, étape 3) : titre = nom de l'expéditeur, texte = le
 * message, ou « a partagé une vidéo ». Un appui ouvre directement la conversation. Une par
 * conversation : le message suivant remplace le précédent.
 */
object MessageNotifications {
    const val CHANNEL_ID = "messages"

    /** Extra de l'intention qui ouvre [MainActivity] sur une conversation. */
    const val EXTRA_CONVERSATION_ID = "com.youtubeclone.app.extra.CONVERSATION_ID"

    // Vérifiée par l'appelant : sans autorisation, notify ne fait rien.
    @SuppressLint("MissingPermission")
    fun show(context: Context, alert: MessageAlert) {
        // Conversation à l'écran : le message y arrive déjà en direct (décision 8).
        if (VisibleConversation.id == alert.conversationId) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.notifications_channel_messages))
                .build(),
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_messages)
            .setContentTitle(alert.senderName)
            .setContentText(alert.text ?: context.getString(R.string.messages_shared_video))
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.text ?: context.getString(R.string.messages_shared_video)))
            .setContentIntent(openConversationIntent(context, alert.conversationId))
            .setAutoCancel(true)
            .build()
        manager.notify(alert.conversationId.hashCode(), notification)
    }

    private fun openConversationIntent(context: Context, conversationId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_CONVERSATION_ID, conversationId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            conversationId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
