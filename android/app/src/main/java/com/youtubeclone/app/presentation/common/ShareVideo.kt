package com.youtubeclone.app.presentation.common

import android.content.Context
import android.content.Intent
import com.youtubeclone.app.R
import com.youtubeclone.app.domain.model.Video

/**
 * Ouvre le partage Android pour une vidéo. Il n'y a pas encore d'adresse web publique
 * des vidéos : on partage le titre et le nom de la chaîne.
 */
fun shareVideo(context: Context, video: Video) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, video.title)
        putExtra(Intent.EXTRA_TEXT, "${video.title} · ${video.channel.name}")
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.watch_share_chooser)))
}
