package com.youtubeclone.app.presentation.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.youtubeclone.app.R
import com.youtubeclone.app.presentation.common.AppLogo
import com.youtubeclone.app.presentation.theme.YoutubeCloneTheme

// Écran d'accueil du tout premier lancement, d'après la capture connexion-03 (iOS) : visuel,
// logo et nom de l'application, une phrase, puis Commencer et Se connecter. Un seul écran ;
// les centres d'intérêt se choisissent ensuite depuis l'onglet « Vous ».

/**
 * [onLoginClick] : l'écran est enregistré comme vu, puis la connexion s'ouvre par-dessus
 * le fil d'accueil (voir AppRoot).
 */
@Composable
fun OnboardingScreen(
    onLoginClick: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    OnboardingContent(
        onStartClick = viewModel::complete,
        onLoginClick = {
            onLoginClick()
            viewModel.complete()
        },
    )
}

@Composable
private fun OnboardingContent(onStartClick: () -> Unit, onLoginClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        VideoMosaic(Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            AppLogo(height = 64.dp)
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 20.dp),
            )
            Text(
                text = stringResource(R.string.onboarding_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onStartClick,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onBackground,
                        contentColor = MaterialTheme.colorScheme.background,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text(stringResource(R.string.onboarding_start), fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onLoginClick,
                    shape = CircleShape,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text(stringResource(R.string.login_submit))
                }
            }
        }
    }
}

// Visuel plein écran : une mosaïque inclinée de vignettes vidéo, quelques-unes en rouge,
// qui s'efface vers le bas sous le texte. Dessinée en code, sans image à embarquer.
@Composable
private fun VideoMosaic(modifier: Modifier = Modifier) {
    val tile = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.background
    Canvas(modifier) {
        val tileWidth = size.width * 0.42f
        val tileHeight = tileWidth * 9f / 16f
        val gap = tileWidth * 0.08f
        rotate(degrees = -12f, pivot = Offset(size.width / 2, size.height * 0.3f)) {
            var row = 0
            var y = -tileHeight * 2
            while (y < size.height * 0.75f) {
                // Rangées décalées d'une demi-vignette, comme un mur d'affiches.
                var x = -tileWidth + (if (row % 2 == 0) 0f else tileWidth / 2)
                var column = 0
                while (x < size.width + tileWidth) {
                    val isAccent = (row * 3 + column) % 7 == 2
                    drawRoundRect(
                        color = if (isAccent) accent.copy(alpha = 0.85f) else tile,
                        topLeft = Offset(x, y),
                        size = Size(tileWidth, tileHeight),
                        cornerRadius = CornerRadius(tileHeight * 0.12f),
                    )
                    x += tileWidth + gap
                    column++
                }
                y += tileHeight + gap
                row++
            }
        }
        // Fondu vers le fond : le texte et les boutons restent sur un aplat lisible.
        drawRect(
            brush = Brush.verticalGradient(
                0f to background.copy(alpha = 0.15f),
                0.35f to background.copy(alpha = 0.55f),
                0.6f to background,
            ),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F0F0F, widthDp = 360, heightDp = 760)
@Composable
private fun OnboardingContentPreview() {
    YoutubeCloneTheme {
        OnboardingContent(onStartClick = {}, onLoginClick = {})
    }
}
