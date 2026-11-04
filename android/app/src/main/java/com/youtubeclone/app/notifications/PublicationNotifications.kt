package com.youtubeclone.app.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.youtubeclone.app.MainActivity
import com.youtubeclone.app.R
import com.youtubeclone.app.data.remote.resolveMediaUrl
import kotlinx.coroutines.withTimeoutOrNull

/** Une publication annoncée par le serveur (ADR-020), telle que reçue dans le message FCM. */
data class PublicationAlert(
    val videoId: String,
    val channelName: String,
    val title: String,
    val thumbnailUrl: String?,
)

/**
 * Notification « nouvelle vidéo » (ADR-020) : titre = nom de la chaîne, texte = titre de la vidéo,
 * image = miniature. Un appui ouvre directement la vidéo.
 */
object PublicationNotifications {
    const val CHANNEL_ID = "publications"

    /** Extra de l'intention qui ouvre [MainActivity] sur une vidéo. */
    const val EXTRA_VIDEO_ID = "com.youtubeclone.app.extra.VIDEO_ID"

    private const val IMAGE_TIMEOUT_MS = 8_000L

    // Vérifiée par l'appelant : sans autorisation, notify ne fait rien.
    @SuppressLint("MissingPermission")
    suspend fun show(context: Context, alert: PublicationAlert) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notifications_channel_publications))
                .build(),
        )
        val thumbnail = alert.thumbnailUrl?.let { loadBitmap(context, resolveMediaUrl(it)) }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(alert.channelName)
            .setContentText(alert.title)
            .setContentIntent(openVideoIntent(context, alert.videoId))
            .setAutoCancel(true)
            .apply {
                if (thumbnail != null) {
                    setLargeIcon(thumbnail)
                    setStyle(NotificationCompat.BigPictureStyle().bigPicture(thumbnail).bigLargeIcon(null as Bitmap?))
                }
            }
            .build()
        manager.notify(alert.videoId.hashCode(), notification)
    }

    private fun openVideoIntent(context: Context, videoId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_VIDEO_ID, videoId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            videoId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // Miniature indisponible (serveur injoignable, délai dépassé) : la notification part sans image.
    private suspend fun loadBitmap(context: Context, url: String): Bitmap? = withTimeoutOrNull(IMAGE_TIMEOUT_MS) {
        val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
        (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
    }
}
