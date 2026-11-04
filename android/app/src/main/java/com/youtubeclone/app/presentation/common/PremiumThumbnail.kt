package com.youtubeclone.app.presentation.common

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.youtubeclone.app.R

/**
 * Le compte qui regarde a un abonnement Premium actif. Fourni une fois, à la racine de la
 * navigation : toutes les miniatures en dépendent (ADR-026).
 */
val LocalIsPremiumViewer = compositionLocalOf { false }

// Modifier.blur n'agit qu'à partir d'Android 12 ; en dessous, l'image n'est qu'assombrie (ADR-026).
private val CanBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Miniature d'une vidéo, qui remplit [modifier]. Vidéo Premium et compte sans abonnement : floutée
 * (assombrie avant Android 12), avec un cadenas au centre. On voit que la vidéo existe et qu'elle
 * est réservée ; un compte Premium la voit nette. L'accès à la vidéo reste vérifié par le serveur.
 */
@Composable
fun VideoThumbnailImage(url: String?, isPremiumVideo: Boolean, modifier: Modifier = Modifier) {
    val locked = isPremiumVideo && !LocalIsPremiumViewer.current
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .then(if (locked && CanBlur) Modifier.blur(BlurRadius) else Modifier),
        )
        if (locked) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = if (CanBlur) 0.25f else 0.6f)),
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lock),
                    contentDescription = stringResource(R.string.premium_locked),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** Badge « Premium » d'une vidéo exclusive, en haut à gauche de sa miniature. */
@Composable
fun PremiumBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_premium),
            contentDescription = null,
            tint = Color(0xFFFFC107),
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = stringResource(R.string.premium_badge),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

private val BlurRadius = 16.dp
